package llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;

/**
 * Ollama {@code POST /api/generate} — локальная или удалённая (nginx + basic auth) Ollama.
 * Тот же клиент работает и с localhost:11434, и с https://ollama-box2.qa.guru.
 */
public final class OllamaClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private final String model;
    private final String basicAuth;   // "user:password" или null
    private final int maxTokens;
    private final Duration timeout;

    public OllamaClient(String baseUrl, String model, String basicAuth, int maxTokens, Duration timeout) {
        this.baseUrl = trimSlash(baseUrl);
        this.model = model;
        this.basicAuth = basicAuth == null || basicAuth.isBlank() ? null : basicAuth;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
    }

    @Override
    public LlmResponse complete(String system, String user) throws LlmException, InterruptedException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("system", system == null ? "" : system);
        body.put("prompt", user == null ? "" : user);
        body.putObject("options").put("temperature", 0).put("num_predict", maxTokens);

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.PROVIDER_ERROR, e.getMessage(), e);
        }
        if (basicAuth != null) {
            builder.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes()));
        }

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmException.Kind.TIMEOUT,
                    "Ollama timeout at " + baseUrl + ": " + e.getMessage(), e);
        } catch (ConnectException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Ollama unavailable at " + baseUrl + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Ollama I/O: " + e.getMessage(), e);
        }
        long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmException(LlmException.httpKind(response.statusCode()),
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return parse(response.body(), durationMs);
    }

    static LlmResponse parse(String body, long durationMs) throws LlmException {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.PARSER_ERROR, "JSON: " + e.getMessage(), e);
        }
        if (root.hasNonNull("error")) {
            throw new LlmException(LlmException.Kind.PROVIDER_ERROR,
                    "ollama error: " + root.get("error").asText());
        }
        String text = root.path("response").asText("");
        if (text.isBlank()) {
            throw new LlmException(LlmException.Kind.EMPTY_RESPONSE, "empty response");
        }
        return new LlmResponse(text,
                intOrNull(root, "prompt_eval_count"),
                intOrNull(root, "eval_count"),
                durationMs);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.has(field) && node.get(field).isNumber() ? node.get(field).asInt() : null;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
