package com.salesmanager.test.shop.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SmtpStub implements AutoCloseable {

  private final ServerSocket server;
  private final List<String> recipients = new CopyOnWriteArrayList<>();
  private final Thread thread;
  private volatile Socket activeSocket;

  public SmtpStub() throws IOException {
    server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
    thread = new Thread(this::serve, "smtp-stub-" + server.getLocalPort());
    thread.setDaemon(true);
    thread.start();
  }

  public int port() {
    return server.getLocalPort();
  }

  public List<String> recipients() {
    return recipients;
  }

  private void serve() {
    while (!server.isClosed()) {
      try (Socket socket = server.accept()) {
        activeSocket = socket;
        socket.setSoTimeout(10000);
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {
          reply(out, "220 stub");
          String line;
          boolean data = false;
          while ((line = in.readLine()) != null) {
            String upper = line.toUpperCase();
            if (upper.startsWith("QUIT")) {
              reply(out, "221 bye");
              break;
            }
            if (data) {
              if (line.equals(".")) {
                data = false;
                reply(out, "250 queued");
              }
              continue;
            }
            if (upper.startsWith("RCPT TO")) {
              recipients.add(line);
              reply(out, "250 ok");
            } else if (upper.startsWith("DATA")) {
              data = true;
              reply(out, "354 go");
            } else if (upper.startsWith("AUTH")) {
              reply(out, "235 authenticated");
            } else {
              reply(out, "250 ok");
            }
          }
        }
      } catch (IOException e) {
        if (!server.isClosed()) {
          continue;
        }
      } finally {
        activeSocket = null;
      }
    }
  }

  private static void reply(PrintWriter out, String line) {
    out.print(line + "\r\n");
    out.flush();
  }

  @Override
  public void close() throws IOException {
    server.close();
    Socket socket = activeSocket;
    if (socket != null) {
      socket.close();
    }
    try {
      thread.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
