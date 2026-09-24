package llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchTableTest {

    @Test
    void renders_markdown_rows() {
        ProviderConfig ok = new ProviderConfig("local", "ollama", "u", "m1", "", "", 0, 0, "n1");
        ProviderConfig skip = new ProviderConfig("cloud", "openai", "u", "m2", "", "", 0.1, 0.2, "n2");
        String table = BenchMain.BenchTable.render(List.of(
                new BenchMain.BenchRow(ok, "ok", 120L, 10, 20, 0.0, "n1"),
                BenchMain.BenchRow.skipped(skip, "нет: key")));
        assertTrue(table.contains("| provider | model | status |"));
        assertTrue(table.contains("| local | m1 | ok | 120 | 10 | 20 | 0.00000 | n1 |"));
        assertTrue(table.contains("| cloud | m2 | skip | — | — | — | — | нет: key |"));
    }
}
