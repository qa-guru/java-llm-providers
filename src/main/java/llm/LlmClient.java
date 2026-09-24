package llm;

/**
 * Единый интерфейс «спроси у модели».
 * Все провайдеры урока — реализации этого контракта.
 */
public interface LlmClient {

    /**
     * Один запрос system+user → текст ответа.
     * @throws LlmException инфраструктурная ошибка (сеть/ключ/формат)
     * @throws InterruptedException прервано ожидание
     */
    LlmResponse complete(String system, String user) throws LlmException, InterruptedException;
}
