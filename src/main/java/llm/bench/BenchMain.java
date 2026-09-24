package llm.bench;

import llm.client.LlmClient;
import llm.client.LlmException;
import llm.client.LlmResponse;
import llm.config.ProviderConfig;
import llm.config.Providers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Один и тот же промпт во все настроенные провайдеры → таблица.
 *   ./gradlew bench                          # все ready-провайдеры, 1 прогон
 *   ./gradlew bench -Drepeat=3               # медиана трёх прогонов
 *   ./gradlew bench -Dout=bench-out/bench.md # + markdown-файл для отчёта
 */
public final class BenchMain {

    private static final String DEFAULT_PROMPT =
            "Напиши один JUnit5-тест на Selenide: открыть /login, ввести неверный пароль, " +
            "проверить текст 'Wrong login or password'. Только код, без пояснений.";

    public static void main(String[] args) throws Exception {
        Properties props = Providers.load(Path.of(System.getProperty("config", "providers.properties")));
        String prompt = System.getProperty("prompt", props.getProperty("bench.prompt", DEFAULT_PROMPT));
        String system = props.getProperty("bench.system", "You are a concise assistant for a QA engineer.");
        int repeat = Integer.getInteger("repeat", 1);
        int maxTokens = Integer.getInteger("maxTokens", 256);
        Duration timeout = Duration.ofSeconds(Integer.getInteger("timeoutSec", 180));

        List<BenchRow> rows = new ArrayList<>();
        for (ProviderConfig cfg : Providers.configured(props)) {
            if (!cfg.ready()) {
                rows.add(BenchRow.skipped(cfg, "нет: " + String.join(", ", cfg.missing())));
                continue;
            }
            LlmClient client = Providers.client(cfg, maxTokens, timeout);
            List<Long> times = new ArrayList<>();
            LlmResponse last = null;
            LlmException failure = null;
            for (int i = 0; i < repeat; i++) {
                try {
                    last = client.complete(system, prompt);
                    times.add(last.durationMs());
                } catch (LlmException e) {
                    failure = e;
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure = new LlmException(LlmException.Kind.TIMEOUT, "interrupted", e);
                    break;
                }
            }
            if (failure != null) {
                rows.add(BenchRow.failed(cfg, failure.kind().name()));
            } else {
                rows.add(BenchRow.ok(cfg, median(times), last.tokensIn(), last.tokensOut(),
                        cfg.estimateCostUsd(last.tokensIn(), last.tokensOut())));
            }
            System.err.print("."); // прогресс в stderr, таблица — в stdout
        }
        System.err.println();

        String table = BenchTable.render(rows);
        System.out.println(table);

        String out = System.getProperty("out");
        if (out != null && !out.isBlank()) {
            Path outPath = Path.of(out);
            Files.createDirectories(outPath.toAbsolutePath().getParent());
            Files.writeString(outPath, table + "\n");
            System.err.println("table -> " + outPath);
        }
    }

    private static long median(List<Long> values) {
        values.sort(null);
        return values.get(values.size() / 2);
    }

    /** Одна строка итоговой таблицы. */
    record BenchRow(ProviderConfig cfg, String status, Long ms,
                    Integer tokensIn, Integer tokensOut, Double costUsd, String comment) {

        static BenchRow ok(ProviderConfig cfg, long ms, Integer in, Integer out, Double cost) {
            return new BenchRow(cfg, "ok", ms, in, out, cost, cfg.note());
        }

        static BenchRow failed(ProviderConfig cfg, String kind) {
            return new BenchRow(cfg, "FAIL " + kind, null, null, null, null, cfg.note());
        }

        static BenchRow skipped(ProviderConfig cfg, String why) {
            return new BenchRow(cfg, "skip", null, null, null, null, why);
        }
    }

    /** Отрисовка markdown-таблицы (одна и для консоли, и для отчёта). */
    static final class BenchTable {

        static String render(List<BenchRow> rows) {
            StringBuilder sb = new StringBuilder();
            sb.append("| provider | model | status | ms | tok in | tok out | ~$ по прайсу | note |\n");
            sb.append("|----------|-------|--------|----|--------|---------|--------------|------|\n");
            for (BenchRow r : rows) {
                sb.append("| ").append(r.cfg().name())
                        .append(" | ").append(r.cfg().model())
                        .append(" | ").append(r.status())
                        .append(" | ").append(r.ms() == null ? "—" : r.ms())
                        .append(" | ").append(r.tokensIn() == null ? "—" : r.tokensIn())
                        .append(" | ").append(r.tokensOut() == null ? "—" : r.tokensOut())
                        .append(" | ").append(r.costUsd() == null ? "—" : String.format("%.5f", r.costUsd()))
                        .append(" | ").append(r.comment() == null ? "" : r.comment())
                        .append(" |\n");
            }
            return sb.toString();
        }
    }
}
