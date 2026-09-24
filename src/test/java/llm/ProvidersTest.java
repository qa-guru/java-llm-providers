package llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ProvidersTest {

    @Test
    void configured_reads_providers_in_order() {
        Properties props = new Properties();
        props.setProperty("providers", "a, b ,c");
        props.setProperty("provider.a.type", "ollama");
        props.setProperty("provider.a.url", "http://localhost:11434");
        props.setProperty("provider.a.model", "m1");
        props.setProperty("provider.b.type", "openai");
        props.setProperty("provider.b.url", "https://x");
        props.setProperty("provider.b.model", "m2");
        props.setProperty("provider.b.key", "k");
        props.setProperty("provider.c.type", "openai");
        props.setProperty("provider.c.url", "https://y");
        props.setProperty("provider.c.model", "m3"); // нет key → skip

        List<ProviderConfig> all = Providers.configured(props);
        assertEquals(List.of("a", "b", "c"), all.stream().map(ProviderConfig::name).toList());
        assertTrue(all.get(0).ready());
        assertTrue(all.get(1).ready());
        assertFalse(all.get(2).ready());
        assertEquals(List.of("key"), all.get(2).missing());
        assertEquals(2, Providers.ready(props).size());
        assertEquals(1, Providers.skipped(props).size());
    }

    @Test
    void substituteEnv_replaces_env_and_default() {
        Properties props = new Properties();
        props.setProperty("a", "${DEFINITELY_MISSING_ENV_XYZ:fallback}");
        props.setProperty("b", "prefix-${DEFINITELY_MISSING_ENV_XYZ}-suffix");
        props.setProperty("c", "plain");
        Providers.substituteEnv(props);
        assertEquals("fallback", props.getProperty("a"));
        assertEquals("prefix--suffix", props.getProperty("b"));
        assertEquals("plain", props.getProperty("c"));
    }

    @Test
    void missing_unresolved_placeholder_counts_as_missing() {
        Properties props = new Properties();
        props.setProperty("providers", "p");
        props.setProperty("provider.p.type", "openai");
        props.setProperty("provider.p.url", "${NO_URL_ENV}");
        props.setProperty("provider.p.model", "m");
        props.setProperty("provider.p.key", "k");
        ProviderConfig cfg = Providers.configured(props).get(0);
        // ${...} остался в значении → url считается отсутствующим
        Providers.substituteEnv(props);
        ProviderConfig resolved = Providers.configured(props).get(0);
        assertFalse(resolved.ready());
        assertTrue(resolved.missing().contains("url"));
        assertFalse(cfg.ready());
    }

    @Test
    void client_factory_by_type() {
        ProviderConfig ollama = new ProviderConfig("o", "ollama", "http://x", "m", "", "", 0, 0, "");
        ProviderConfig openai = new ProviderConfig("p", "openai", "https://x", "m", "k", "", 0, 0, "");
        ProviderConfig weird = new ProviderConfig("w", "grpc", "x", "m", "", "", 0, 0, "");
        assertInstanceOf(OllamaClient.class, Providers.client(ollama, 10, java.time.Duration.ofSeconds(1)));
        assertInstanceOf(OpenAiCompatibleClient.class, Providers.client(openai, 10, java.time.Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Providers.client(weird, 10, java.time.Duration.ofSeconds(1)));
    }

    @Test
    void estimateCost_uses_declared_price() {
        ProviderConfig cfg = new ProviderConfig("p", "openai", "u", "m", "k", "",
                0.14, 0.28, "");
        assertEquals(0.000042, cfg.estimateCostUsd(100, 100), 1e-9);
        assertNull(cfg.estimateCostUsd(null, null));
        assertEquals(0.0, new ProviderConfig("l", "ollama", "u", "m", "", "", 0, 0, "")
                .estimateCostUsd(50, 50));
    }
}
