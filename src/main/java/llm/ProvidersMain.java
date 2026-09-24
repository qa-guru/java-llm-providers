package llm;

import llm.config.ProviderConfig;
import llm.config.Providers;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Кто готов к вызову, кто пропущен и почему:
 *   ./gradlew providers
 */
public final class ProvidersMain {

    public static void main(String[] args) throws Exception {
        Properties props = Providers.load(Path.of(System.getProperty("config", "providers.properties")));
        List<ProviderConfig> all = Providers.configured(props);
        if (all.isEmpty()) {
            System.out.println("providers= пуст — открой providers.properties");
            return;
        }
        System.out.printf("%-16s %-8s %-34s %-10s %s%n", "provider", "type", "model", "status", "comment");
        for (ProviderConfig cfg : all) {
            String status = cfg.ready() ? "READY" : "skip";
            String comment = cfg.ready()
                    ? cfg.note()
                    : "нет: " + String.join(", ", cfg.missing());
            System.out.printf("%-16s %-8s %-34s %-10s %s%n",
                    cfg.name(), cfg.type(), shorten(cfg.model(), 34), status, comment);
        }
    }

    private static String shorten(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
