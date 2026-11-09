package com.salesmanager.test.aws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal in-memory S3 (path style) and SES endpoint used to exercise the AWS SDK v2 call sites
 * without network access. Point the SDK at it with the {@code aws.endpointUrl} system property.
 */
public class FakeAwsServer implements AutoCloseable {

	public static class RecordedRequest {
		public final String method;
		public final String path;
		public final String query;
		public final Map<String, String> headers;
		public final byte[] body;

		RecordedRequest(String method, String path, String query, Map<String, String> headers, byte[] body) {
			this.method = method;
			this.path = path;
			this.query = query;
			this.headers = headers;
			this.body = body;
		}

		public String header(String name) {
			return headers.get(name.toLowerCase());
		}

		public String bodyAsString() {
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	private final HttpServer server;
	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
	private final Map<String, String> contentTypes = new ConcurrentHashMap<>();
	private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

	public FakeAwsServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	public String endpoint() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public Map<String, byte[]> objects() {
		return objects;
	}

	public String contentType(String bucketAndKey) {
		return contentTypes.get(bucketAndKey);
	}

	public List<RecordedRequest> requests() {
		return requests;
	}

	private void handle(HttpExchange ex) throws IOException {
		try {
			Map<String, String> headers = new TreeMap<>();
			ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
			byte[] raw = readAll(ex.getRequestBody());
			byte[] body = isAwsChunked(headers) ? decodeAwsChunked(raw) : raw;
			String path = ex.getRequestURI().getRawPath();
			String query = ex.getRequestURI().getRawQuery();
			requests.add(new RecordedRequest(ex.getRequestMethod(), path, query, headers, body));

			if ("POST".equals(ex.getRequestMethod()) && "/".equals(path)) {
				// SES query protocol
				send(ex, 200, "text/xml", "<SendEmailResponse xmlns=\"http://ses.amazonaws.com/doc/2010-12-01/\">"
						+ "<SendEmailResult><MessageId>fake-message-id</MessageId></SendEmailResult>"
						+ "<ResponseMetadata><RequestId>fake-request</RequestId></ResponseMetadata></SendEmailResponse>");
				return;
			}

			String objectPath = URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
			int slash = objectPath.indexOf('/');
			String bucket = slash < 0 ? objectPath : objectPath.substring(0, slash);
			String key = slash < 0 ? "" : objectPath.substring(slash + 1);

			switch (ex.getRequestMethod()) {
			case "PUT":
				objects.put(bucket + "/" + key, body);
				if (headers.get("content-type") != null) {
					contentTypes.put(bucket + "/" + key, headers.get("content-type"));
				}
				ex.getResponseHeaders().add("ETag", "\"fake-etag\"");
				send(ex, 200, null, "");
				return;
			case "DELETE":
				objects.remove(bucket + "/" + key);
				send(ex, 204, null, null);
				return;
			case "GET":
				if (key.isEmpty() && query != null && query.contains("list-type=2")) {
					String prefix = queryParam(query, "prefix");
					StringBuilder xml = new StringBuilder(
							"<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
					xml.append("<Name>").append(bucket).append("</Name><IsTruncated>false</IsTruncated>");
					int count = 0;
					for (Map.Entry<String, byte[]> e : new TreeMap<>(objects).entrySet()) {
						if (!e.getKey().startsWith(bucket + "/")) {
							continue;
						}
						String k = e.getKey().substring(bucket.length() + 1);
						if (prefix == null || k.startsWith(prefix)) {
							xml.append("<Contents><Key>").append(k).append("</Key><Size>").append(e.getValue().length)
									.append("</Size></Contents>");
							count++;
						}
					}
					xml.append("<KeyCount>").append(count).append("</KeyCount></ListBucketResult>");
					send(ex, 200, "application/xml", xml.toString());
					return;
				}
				byte[] content = objects.get(bucket + "/" + key);
				if (content == null) {
					send(ex, 404, "application/xml",
							"<Error><Code>NoSuchKey</Code><Message>not found</Message></Error>");
					return;
				}
				ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
				ex.sendResponseHeaders(200, content.length);
				ex.getResponseBody().write(content);
				ex.close();
				return;
			default:
				send(ex, 405, null, "");
			}
		} catch (RuntimeException e) {
			send(ex, 500, "text/plain", e.toString());
		}
	}

	private static String queryParam(String query, String name) {
		for (String p : query.split("&")) {
			int eq = p.indexOf('=');
			String k = eq < 0 ? p : p.substring(0, eq);
			if (k.equals(name)) {
				return eq < 0 ? "" : URLDecoder.decode(p.substring(eq + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	private static boolean isAwsChunked(Map<String, String> headers) {
		String enc = headers.getOrDefault("content-encoding", "");
		String sha = headers.getOrDefault("x-amz-content-sha256", "");
		return enc.contains("aws-chunked") || sha.startsWith("STREAMING-");
	}

	/** decodes the aws-chunked framing (size[;ext]\r\n data \r\n ... 0\r\n trailers) */
	private static byte[] decodeAwsChunked(byte[] raw) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			int lineEnd = indexOfCrlf(raw, pos);
			if (lineEnd < 0) {
				break;
			}
			String sizeLine = new String(raw, pos, lineEnd - pos, StandardCharsets.US_ASCII);
			int semi = sizeLine.indexOf(';');
			int size = Integer.parseInt((semi < 0 ? sizeLine : sizeLine.substring(0, semi)).trim(), 16);
			pos = lineEnd + 2;
			if (size == 0) {
				break;
			}
			out.write(raw, pos, size);
			pos += size + 2;
		}
		return out.toByteArray();
	}

	private static int indexOfCrlf(byte[] b, int from) {
		for (int i = from; i < b.length - 1; i++) {
			if (b[i] == '\r' && b[i + 1] == '\n') {
				return i;
			}
		}
		return -1;
	}

	private static byte[] readAll(InputStream in) throws IOException {
		return in.readAllBytes();
	}

	private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
		if (contentType != null) {
			ex.getResponseHeaders().add("Content-Type", contentType);
		}
		if (body == null) {
			ex.sendResponseHeaders(status, -1);
		} else {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				ex.getResponseBody().write(bytes);
			}
		}
		ex.close();
	}

	@Override
	public void close() {
		server.stop(0);
	}

	/** system properties read by the SDK v2 default chains when a client is built */
	public static final String[] SDK_PROPERTIES = { "aws.endpointUrl", "aws.accessKeyId", "aws.secretAccessKey",
			"aws.region" };

	public void configureSdk() {
		System.setProperty("aws.endpointUrl", endpoint());
		System.setProperty("aws.accessKeyId", "AKIAFAKEFORTESTS");
		System.setProperty("aws.secretAccessKey", "fake-secret-for-tests");
	}

	public static void clearSdk() {
		for (String p : SDK_PROPERTIES) {
			System.clearProperty(p);
		}
	}
}
