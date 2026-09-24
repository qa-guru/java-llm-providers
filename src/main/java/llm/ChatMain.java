package llm;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Один промпт в один провайдер:
 *   ./gradlew chat -Dprovider=ollama-local -Dprompt="Привет"
 */
public final class ChatMain {

    public static void main(String[] args) throws Exception {
        String providerName = System.getProperty("provider", "ollama-local");
        String prompt = System.getProperty("prompt",
                "Объясни QA-инженеру в двух предложениях, что такое flaky test.");
        String system = System.getProperty("system", "You are a concise assistant for a QA engineer.");
        int maxTokens = Integer.getInteger("maxTokens", 256);
        Duration timeout = Duration.ofSeconds(Integer.getInteger("timeoutSec", 120));

        Properties props = Providers.load(Path.of(System.getProperty("config", "providers.properties")));
        ProviderConfig cfg = Providers.configured(props).stream()
                .filter(c -> c.name().equals(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider '" + providerName + "' not in providers= list of providers.properties"));
        if (!cfg.ready()) {
            System.out.println("SKIP " + cfg.name() + " — не хватает: " + String.join(", ", cfg.missing()));
            System.out.println("Заведи переменные окружения и повтори. См. providers.properties.");
            return;
        }

        LlmClient client = Providers.client(cfg, maxTokens, timeout);
        LlmResponse r = client.complete(system, prompt);
        System.out.println(r.text());
        System.out.println("---");
        System.out.printf("%s · %s · %d ms · in %s tok / out %s tok · ≈$%s%n",
                cfg.name(), cfg.model(), r.durationMs(),
                r.tokensIn() == null ? "?" : r.tokensIn(),
                r.tokensOut() == null ? "?" : r.tokensOut(),
                costLabel(cfg.estimateCostUsd(r.tokensIn(), r.tokensOut())));
    }

    static String costLabel(Double cost) {
        return cost == null ? "?" : String.format("%.5f", cost);
    }
}
