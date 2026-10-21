package com.salesmanager.test.shop.contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

public final class OperationInventory {

	private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/{}]+\\}");
	private static final Set<String> HTTP_METHODS = new TreeSet<>(Arrays.asList(
			"get", "put", "post", "delete", "options", "head", "patch", "trace"));

	private OperationInventory() {
	}

	public static List<Operation> normalize(JsonNode specification) {
		boolean swagger2 = "2.0".equals(specification.path("swagger").asText());
		boolean openApi3 = specification.path("openapi").asText().startsWith("3.");
		if (!swagger2 && !openApi3) {
			throw new IllegalArgumentException("Expected a Swagger 2.0 or OpenAPI 3.x document");
		}

		JsonNode paths = specification.path("paths");
		List<Operation> operations = new ArrayList<>();
		Iterator<Map.Entry<String, JsonNode>> pathsIterator = paths.fields();
		while (pathsIterator.hasNext()) {
			Map.Entry<String, JsonNode> pathEntry = pathsIterator.next();
			JsonNode pathItem = pathEntry.getValue();
			Iterator<Map.Entry<String, JsonNode>> methodsIterator = pathItem.fields();
			while (methodsIterator.hasNext()) {
				Map.Entry<String, JsonNode> methodEntry = methodsIterator.next();
				String method = methodEntry.getKey().toLowerCase();
				if (!HTTP_METHODS.contains(method)) {
					continue;
				}
				operations.add(operation(specification, pathEntry.getKey(), method.toUpperCase(),
						pathItem, methodEntry.getValue(), swagger2));
			}
		}
		operations.sort(Comparator.comparing((Operation operation) -> operation.path)
				.thenComparing(operation -> operation.method));
		return operations;
	}

	private static Operation operation(JsonNode specification, String path, String method,
			JsonNode pathItem, JsonNode source, boolean swagger2) {
		Operation operation = new Operation();
		operation.method = method;
		operation.path = path;
		operation.key = key(method, path);
		operation.operationId = source.path("operationId").asText("");
		operation.tags = stringValues(source.path("tags"));

		Map<String, Parameter> parameters = new TreeMap<>();
		addParameters(parameters, pathItem.path("parameters"));
		addParameters(parameters, source.path("parameters"));
		operation.parameters.addAll(parameters.values());

		if (swagger2) {
			JsonNode consumes = source.has("consumes") ? source.path("consumes") : specification.path("consumes");
			JsonNode produces = source.has("produces") ? source.path("produces") : specification.path("produces");
			operation.consumes.addAll(stringValues(consumes));
			operation.produces.addAll(stringValues(produces));
		} else {
			JsonNode requestContent = source.path("requestBody").path("content");
			operation.consumes.addAll(fieldNames(requestContent));
			JsonNode responses = source.path("responses");
			Iterator<JsonNode> responseIterator = responses.elements();
			while (responseIterator.hasNext()) {
				operation.produces.addAll(fieldNames(responseIterator.next().path("content")));
			}
		}

		JsonNode declaredSecurity = source.has("security") ? source.path("security") : specification.path("security");
		operation.declaredSecurity.addAll(securityNames(declaredSecurity));
		operation.security = securityClass(path);
		return operation;
	}

	private static void addParameters(Map<String, Parameter> parameters, JsonNode source) {
		for (JsonNode value : source) {
			String in = value.path("in").asText("");
			if ("body".equals(in)) {
				continue;
			}
			Parameter parameter = new Parameter();
			parameter.name = value.path("name").asText("");
			parameter.in = in;
			parameter.required = value.path("required").asBoolean(false);
			parameters.put(parameter.name + "\u0000" + parameter.in, parameter);
		}
	}

	private static List<String> stringValues(JsonNode values) {
		List<String> result = new ArrayList<>();
		for (JsonNode value : values) {
			if (value.isTextual()) {
				result.add(value.asText());
			}
		}
		Collections.sort(result);
		return result;
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		Iterator<String> iterator = node.fieldNames();
		while (iterator.hasNext()) {
			names.add(iterator.next());
		}
		Collections.sort(names);
		return names;
	}

	private static List<String> securityNames(JsonNode security) {
		Set<String> names = new TreeSet<>();
		for (JsonNode requirement : security) {
			Iterator<String> iterator = requirement.fieldNames();
			while (iterator.hasNext()) {
				names.add(iterator.next());
			}
		}
		return new ArrayList<>(names);
	}

	private static String securityClass(String path) {
		if (path.matches("/api/v[^/]+/private/login.*")
				|| path.matches("/api/v[^/]+/private/refresh")) {
			return "public";
		}
		if (path.matches("/api/v[^/]+/private(?:/.*)?")) {
			return "admin-jwt";
		}
		if (path.matches("/api/v[^/]+/auth/(refresh|login|register)")) {
			return "public";
		}
		if (path.matches("/api/v[^/]+/auth(?:/.*)?")) {
			return "customer-jwt";
		}
		if ("/services/public".equals(path) || path.startsWith("/services/public/")) {
			return "public";
		}
		if ("/services".equals(path) || path.startsWith("/services/")) {
			return "basic";
		}
		return "public";
	}

	private static String key(String method, String path) {
		return method + " " + PATH_VARIABLE.matcher(path).replaceAll("{}");
	}

	public static Diff diff(List<Operation> baseline, List<Operation> live) {
		Map<String, Operation> baselineByKey = byKey(baseline);
		Map<String, Operation> liveByKey = byKey(live);
		Diff diff = new Diff();
		for (Map.Entry<String, Operation> entry : baselineByKey.entrySet()) {
			Operation current = liveByKey.get(entry.getKey());
			if (current == null) {
				diff.missing.add(entry.getKey());
			} else if (!entry.getValue().sameContract(current)) {
				diff.changed.add(entry.getKey());
			}
		}
		for (String key : liveByKey.keySet()) {
			if (!baselineByKey.containsKey(key)) {
				diff.added.add(key);
			}
		}
		return diff;
	}

	private static Map<String, Operation> byKey(List<Operation> operations) {
		Map<String, Operation> indexed = new TreeMap<>();
		for (Operation operation : operations) {
			indexed.put(operation.key, operation);
		}
		return indexed;
	}

	public static final class Operation {
		public String method;
		public String path;
		public String key;
		public String operationId;
		public List<String> tags = new ArrayList<>();
		public List<Parameter> parameters = new ArrayList<>();
		public List<String> consumes = new ArrayList<>();
		public List<String> produces = new ArrayList<>();
		public List<String> declaredSecurity = new ArrayList<>();
		public String security;

		public Operation withPath(String newPath) {
			Operation copy = new Operation();
			copy.method = method;
			copy.path = newPath;
			copy.key = key(method, newPath);
			copy.operationId = operationId;
			copy.tags = new ArrayList<>(tags);
			copy.parameters = new ArrayList<>(parameters);
			copy.consumes = new ArrayList<>(consumes);
			copy.produces = new ArrayList<>(produces);
			copy.declaredSecurity = new ArrayList<>(declaredSecurity);
			copy.security = securityClass(newPath);
			return copy;
		}

		private boolean sameContract(Operation other) {
			return parameters.equals(other.parameters)
					&& consumes.equals(other.consumes)
					&& produces.equals(other.produces);
		}
	}

	public static final class Parameter {
		public String name;
		public String in;
		public boolean required;

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Parameter)) {
				return false;
			}
			Parameter parameter = (Parameter) other;
			return required == parameter.required && name.equals(parameter.name) && in.equals(parameter.in);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(name, in, required);
		}
	}

	public static final class Diff {
		public final List<String> missing = new ArrayList<>();
		public final List<String> added = new ArrayList<>();
		public final List<String> changed = new ArrayList<>();

		public boolean isEmpty() {
			return missing.isEmpty() && added.isEmpty() && changed.isEmpty();
		}
	}
}
