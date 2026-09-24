package llm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live-смоук: реальный вызов провайдера.
 *   ./gradlew test -DincludeTags=live -Dprovider=ollama-local
 * Пропускается, если провайдер не настроен (нет ключа/url).
 */
@Tag("live")
class LiveSmokeTest {

    @Test
    void one_provider_answers() throws Exception {
        String name = System.getProperty("provider", "ollama-local");
        Properties props = Providers.load(Path.of(System.getProperty("config", "providers.properties")));
        ProviderConfig cfg = Providers.configured(props).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown provider " + name));
        assumeTrue(cfg.ready(), () -> "skipped: " + name + " missing " + cfg.missing());

        LlmClient client = Providers.client(cfg, 64, Duration.ofSeconds(120));
        LlmResponse r = client.complete("You are terse.", "Скажи только: ok");
        assertFalse(r.text().isBlank());
        assertTrue(r.durationMs() > 0);
    }
}
