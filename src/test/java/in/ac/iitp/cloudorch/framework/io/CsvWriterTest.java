package in.ac.iitp.cloudorch.framework.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvWriterTest {

    @Test
    void writesEscapedRowsWithStableNumberFormat() throws IOException {
        Path f = Files.createTempDirectory("csv").resolve("nested/out.csv");
        try (CsvWriter w = new CsvWriter(f, List.of("name", "value", "note"))) {
            w.writeRow(Arrays.asList("FIXED-12", 2.5, "a,b"));
            w.writeRow(Arrays.asList("say \"hi\"", Double.NaN, null));
            w.writeRow(Arrays.asList("int", 3, 4.0));
        }
        List<String> lines = Files.readAllLines(f);
        assertEquals("name,value,note", lines.get(0));
        assertEquals("FIXED-12,2.500000,\"a,b\"", lines.get(1));
        assertEquals("\"say \"\"hi\"\"\",,", lines.get(2));
        assertEquals("int,3,4", lines.get(3));
    }

    @Test
    void rejectsWrongRowWidth() throws IOException {
        Path f = Files.createTempFile("csv", ".csv");
        try (CsvWriter w = new CsvWriter(f, List.of("a", "b"))) {
            assertThrows(IllegalArgumentException.class, () -> w.writeRow(List.of(1)));
        }
    }
}
