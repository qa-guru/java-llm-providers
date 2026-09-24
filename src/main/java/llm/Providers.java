package llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Загрузка providers.properties → ProviderConfig → LlmClient.
 * ${ENV_VAR} и ${ENV_VAR:default} в properties подставляются из окружения —
 * секреты в файле не храним, файл можно коммитить.
 */
public final class Providers {

    private static final Pattern ENV_REF = Pattern.compile("\\$\\{([A-Z0-9_]+)(?::([^}]*))?}");

    private Providers() {
    }

    /** providers.properties из файла; файла нет — classpath; нет и там — пусто. */
    public static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        // Properties.load(InputStream) читает ISO-8859-1 — грузим Reader в UTF-8.
        if (file != null && Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(in);
            }
        } else {
            try (InputStream in = Providers.class.getResourceAsStream("/providers.properties")) {
                if (in != null) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
        }
        substituteEnv(props);
        return props;
    }

    /** Заменить ${VAR} / ${VAR:default} во всех значениях на System.getenv / -D. */
    static void substituteEnv(Properties props) {
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            Matcher m = ENV_REF.matcher(value);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String env = System.getProperty(m.group(1),
                        System.getenv().getOrDefault(m.group(1), m.group(2) == null ? "" : m.group(2)));
                m.appendReplacement(sb, Matcher.quoteReplacement(env));
            }
            m.appendTail(sb);
            props.setProperty(key, sb.toString());
        }
    }

    /** Все провайдеры из списка providers=... — в порядке объявления. */
    public static List<ProviderConfig> configured(Properties props) {
        List<ProviderConfig> out = new ArrayList<>();
        String list = props.getProperty("providers", "");
        for (String raw : list.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            String p = "provider." + name + ".";
            out.add(new ProviderConfig(
                    name,
                    props.getProperty(p + "type", "openai").trim(),
                    props.getProperty(p + "url", "").trim(),
                    props.getProperty(p + "model", "").trim(),
                    props.getProperty(p + "key", "").trim(),
                    props.getProperty(p + "basic", "").trim(),
                    parseDouble(props.getProperty(p + "usdInPerM"), 0),
                    parseDouble(props.getProperty(p + "usdOutPerM"), 0),
                    props.getProperty(p + "note", "").trim()));
        }
        return out;
    }

    /** provider → почему пропущен (пустая мапа = все готовы). */
    public static Map<ProviderConfig, String> skipped(Properties props) {
        Map<ProviderConfig, String> skipped = new LinkedHashMap<>();
        for (ProviderConfig cfg : configured(props)) {
            if (!cfg.ready()) {
                skipped.put(cfg, "нет: " + String.join(", ", cfg.missing()));
            }
        }
        return skipped;
    }

    /** Готовые к вызову провайдеры. */
    public static List<ProviderConfig> ready(Properties props) {
        return configured(props).stream().filter(ProviderConfig::ready).toList();
    }

    /** Фабрика клиента по типу провайдера. */
    public static LlmClient client(ProviderConfig cfg, int maxTokens, Duration timeout) {
        return switch (cfg.type()) {
            case "ollama" -> new OllamaClient(cfg.baseUrl(), cfg.model(), cfg.basicAuth(), maxTokens, timeout);
            case "openai" -> new OpenAiCompatibleClient(cfg.baseUrl(), cfg.apiKey(), cfg.model(), maxTokens, timeout);
            default -> throw new IllegalArgumentException(
                    "Unknown provider type '" + cfg.type() + "' for " + cfg.name() + " (use ollama|openai)");
        };
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
