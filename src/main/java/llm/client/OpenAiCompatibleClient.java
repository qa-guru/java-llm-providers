package llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * OpenAI-compatible {@code POST /v1/chat/completions}.
 * Один клиент покрывает OpenRouter, Groq, GitHub Models, DeepSeek,
 * Yandex Foundation Models, GigaChat (v1), Selectel FMC, Ollama Cloud —
 * всё, что говорит на диалекте OpenAI.
 */
public final class OpenAiCompatibleClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final Duration timeout;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model,
                                  int maxTokens, Duration timeout) {
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
    }

    @Override
    public LlmResponse complete(String system, String user) throws LlmException, InterruptedException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system == null ? "" : system);
        messages.addObject().put("role", "user").put("content", user == null ? "" : user);

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.PROVIDER_ERROR, e.getMessage(), e);
        }
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmException.Kind.TIMEOUT,
                    "timeout at " + baseUrl + ": " + e.getMessage(), e);
        } catch (ConnectException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "endpoint unavailable at " + baseUrl + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "I/O: " + e.getMessage(), e);
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
            JsonNode err = root.get("error");
            String msg = err.isTextual() ? err.asText() : err.path("message").asText(err.toString());
            throw new LlmException(LlmException.Kind.PROVIDER_ERROR, "provider error: " + msg);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmException(LlmException.Kind.EMPTY_RESPONSE, "no choices");
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new LlmException(LlmException.Kind.EMPTY_RESPONSE, "empty message");
        }
        JsonNode usage = root.path("usage");
        return new LlmResponse(content,
                intOrNull(usage, "prompt_tokens"),
                intOrNull(usage, "completion_tokens"),
                durationMs);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isNumber()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
