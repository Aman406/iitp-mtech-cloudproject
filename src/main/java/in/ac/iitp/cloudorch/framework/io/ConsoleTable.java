package in.ac.iitp.cloudorch.framework.io;

import java.util.ArrayList;
import java.util.List;

/** Renders a fixed-width text table for console summaries. */
public final class ConsoleTable {

    private final List<String> header;
    private final List<List<String>> rows = new ArrayList<>();

    public ConsoleTable(List<String> header) {
        this.header = List.copyOf(header);
    }

    public void addRow(List<String> row) {
        if (row.size() != header.size()) {
            throw new IllegalArgumentException("row width does not match header");
        }
        rows.add(List.copyOf(row));
    }

    public String render() {
        int[] width = new int[header.size()];
        for (int i = 0; i < header.size(); i++) {
            width[i] = header.get(i).length();
            for (List<String> r : rows) {
                width[i] = Math.max(width[i], r.get(i).length());
            }
        }
        StringBuilder sb = new StringBuilder();
        appendRow(sb, header, width);
        for (int i = 0; i < width.length; i++) {
            sb.append(i == 0 ? "" : "-+-").append("-".repeat(width[i]));
        }
        sb.append('\n');
        for (List<String> r : rows) {
            appendRow(sb, r, width);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> cells, int[] width) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String c = cells.get(i);
            // left-align the first column (labels), right-align numbers
            sb.append(i == 0 ? c + " ".repeat(width[i] - c.length()) : " ".repeat(width[i] - c.length()) + c);
        }
        sb.append('\n');
    }
}
