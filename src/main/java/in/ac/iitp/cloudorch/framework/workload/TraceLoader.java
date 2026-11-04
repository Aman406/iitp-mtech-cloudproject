package in.ac.iitp.cloudorch.framework.workload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads per-interval arrival counts from a CSV file, so that both research modules can be run on
 * an externally prepared trace instead of the synthetic generator.
 *
 * <p>Accepted formats: a single numeric column with no header, or a header row containing the
 * named column (default {@code arrivals}). Blank lines and lines starting with {@code #} are
 * ignored. Fractional values are rounded to the nearest integer.
 */
public final class TraceLoader {

    private TraceLoader() {
    }

    public static long[] loadCounts(Path csv, String column) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                lines.add(t);
            }
        }
        if (lines.isEmpty()) {
            throw new IOException("trace file is empty: " + csv);
        }
        int col = 0;
        int first = 0;
        String[] head = lines.get(0).split(",");
        if (!isNumber(head[0].trim()) || (head.length > 1 && !isNumber(head[head.length - 1].trim()))) {
            col = -1;
            for (int i = 0; i < head.length; i++) {
                if (head[i].trim().toLowerCase(Locale.ROOT).equals(column.toLowerCase(Locale.ROOT))) {
                    col = i;
                }
            }
            if (col < 0) {
                throw new IOException("column '" + column + "' not found in header of " + csv);
            }
            first = 1;
        }
        long[] counts = new long[lines.size() - first];
        for (int i = first; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (col >= parts.length) {
                throw new IOException("line " + (i + 1) + " has no column " + col);
            }
            String v = parts[col].trim();
            if (!isNumber(v)) {
                throw new IOException("line " + (i + 1) + ": not a number: " + v);
            }
            long c = Math.round(Double.parseDouble(v));
            if (c < 0) {
                throw new IOException("line " + (i + 1) + ": negative count");
            }
            counts[i - first] = c;
        }
        return counts;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
