package llm.client;

/**
 * Один ответ модели.
 * tokensIn/tokensOut — как посчитал провайдер (usage), null если не прислал.
 * durationMs — полное время вызова на нашей стороне.
 */
public record LlmResponse(String text, Integer tokensIn, Integer tokensOut, long durationMs) {
}
