package llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Один провайдер из providers.properties.
 * Цена декларируется в properties (usd*PerM) — API её не возвращает,
 * мы честно считаем по прайсу, который сами записали.
 */
public record ProviderConfig(
        String name,
        String type,          // ollama | openai
        String baseUrl,
        String model,
        String apiKey,        // Bearer для openai
        String basicAuth,     // "user:pass" для ollama за nginx basic auth
        double usdInPerM,     // $ / 1M input tokens по прайсу
        double usdOutPerM,    // $ / 1M output tokens по прайсу
        String note           // человекочитаемая пометка (кусок для таблицы)
) {

    /** Чего не хватает, чтобы провайдер реально заработал. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("${")) {
            missing.add("url");
        }
        if (model == null || model.isBlank() || model.startsWith("${")) {
            missing.add("model");
        }
        if ("openai".equals(type) && (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${"))) {
            missing.add("key");
        }
        return missing;
    }

    public boolean ready() {
        return missing().isEmpty();
    }

    /** Цена одного ответа по прайсу из properties. null — токены неизвестны. */
    public Double estimateCostUsd(Integer tokensIn, Integer tokensOut) {
        if (tokensIn == null && tokensOut == null) {
            return null;
        }
        double cost = (tokensIn == null ? 0 : tokensIn) * usdInPerM / 1_000_000.0
                + (tokensOut == null ? 0 : tokensOut) * usdOutPerM / 1_000_000.0;
        return cost;
    }
}
