package in.ac.iitp.cloudorch.framework.io;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Minimal RFC-4180 style CSV writer. Doubles are written with {@link Locale#ROOT} so the decimal
 * separator is always '.', with six decimals (whole numbers without decimals), and NaN is
 * written as an empty cell (meaning "not applicable").
 */
public final class CsvWriter implements Closeable {

    private final BufferedWriter out;
    private final int columns;

    public CsvWriter(Path file, List<String> header) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        this.columns = header.size();
        writeRow(List.copyOf(header));
    }

    public void writeRow(List<?> values) throws IOException {
        if (values.size() != columns) {
            throw new IllegalArgumentException("row has " + values.size() + " values, header has " + columns);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(format(values.get(i)));
        }
        out.write(sb.toString());
        out.write('\n');
    }

    static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                return "";
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return Long.toString(d.longValue()); // whole numbers (counts) without a decimal part
            }
            return String.format(Locale.ROOT, "%.6f", d);
        }
        if (v instanceof Float f) {
            return format(f.doubleValue());
        }
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
