package llm.client;

/** Инфраструктурная ошибка вызова модели: сеть, авторизация, формат ответа. */
public final class LlmException extends Exception {

    public enum Kind {
        TIMEOUT,          // не дождались ответа
        UNAVAILABLE,      // хост недоступен / 5xx
        AUTH,             // 401/403 — нет или плохой ключ
        RATE_LIMIT,       // 429 — free tier закончился
        PROVIDER_ERROR,   // провайдер ответил ошибкой
        EMPTY_RESPONSE,   // 200, но ответа нет
        PARSER_ERROR,     // тело не JSON
        NOT_CONFIGURED    // нет baseUrl/model/key в properties
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Маппинг HTTP-статуса на Kind — один раз для всех клиентов. */
    public static Kind httpKind(int status) {
        return switch (status) {
            case 401, 403 -> Kind.AUTH;
            case 429 -> Kind.RATE_LIMIT;
            case 408 -> Kind.TIMEOUT;
            default -> status >= 500 ? Kind.UNAVAILABLE : Kind.PROVIDER_ERROR;
        };
    }
}
