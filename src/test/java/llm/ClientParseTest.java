package llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientParseTest {

    @Test
    void ollama_parse_happy() throws Exception {
        String body = "{\"model\":\"q\",\"response\":\"hello \",\"prompt_eval_count\":12,\"eval_count\":5}";
        LlmResponse r = OllamaClient.parse(body, 100);
        assertEquals("hello ", r.text());
        assertEquals(12, r.tokensIn());
        assertEquals(5, r.tokensOut());
    }

    @Test
    void ollama_parse_error() {
        LlmException e = assertThrows(LlmException.class,
                () -> OllamaClient.parse("{\"error\":\"model 'x' not found\"}", 1));
        assertEquals(LlmException.Kind.PROVIDER_ERROR, e.kind());
    }

    @Test
    void openai_parse_happy() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":\"hi\"}}],"
                + "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}";
        LlmResponse r = OpenAiCompatibleClient.parse(body, 50);
        assertEquals("hi", r.text());
        assertEquals(7, r.tokensIn());
        assertEquals(3, r.tokensOut());
    }

    @Test
    void openai_parse_error_object() {
        LlmException e = assertThrows(LlmException.class,
                () -> OpenAiCompatibleClient.parse("{\"error\":{\"message\":\"bad key\"}}", 1));
        assertEquals(LlmException.Kind.PROVIDER_ERROR, e.kind());
        assertTrue(e.getMessage().contains("bad key"));
    }

    @Test
    void openai_parse_no_choices() {
        LlmException e = assertThrows(LlmException.class,
                () -> OpenAiCompatibleClient.parse("{\"choices\":[]}", 1));
        assertEquals(LlmException.Kind.EMPTY_RESPONSE, e.kind());
    }

    @Test
    void httpKind_mapping() {
        assertEquals(LlmException.Kind.AUTH, LlmException.httpKind(401));
        assertEquals(LlmException.Kind.AUTH, LlmException.httpKind(403));
        assertEquals(LlmException.Kind.RATE_LIMIT, LlmException.httpKind(429));
        assertEquals(LlmException.Kind.UNAVAILABLE, LlmException.httpKind(503));
        assertEquals(LlmException.Kind.PROVIDER_ERROR, LlmException.httpKind(400));
    }
}
