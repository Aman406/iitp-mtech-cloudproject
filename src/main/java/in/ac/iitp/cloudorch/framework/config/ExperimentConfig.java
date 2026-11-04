package in.ac.iitp.cloudorch.framework.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Immutable key/value experiment configuration backed by a {@code .properties} file.
 *
 * <p>Values can be overridden from the command line with {@code key=value} arguments so that a
 * single base configuration can drive parameter sweeps. Every typed getter takes a default so that
 * configuration files only need to list the parameters an experiment actually changes.
 */
public final class ExperimentConfig {

    private final Map<String, String> values;

    private ExperimentConfig(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public static ExperimentConfig empty() {
        return new ExperimentConfig(Map.of());
    }

    public static ExperimentConfig of(Map<String, String> values) {
        return new ExperimentConfig(values);
    }

    public static ExperimentConfig load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Map<String, String> m = new TreeMap<>();
        for (String name : p.stringPropertyNames()) {
            m.put(name.trim(), p.getProperty(name).trim());
        }
        return new ExperimentConfig(m);
    }

    /** Returns a new config where each {@code key=value} argument replaces the stored value. */
    public ExperimentConfig withOverrides(List<String> keyValueArgs) {
        Map<String, String> m = new TreeMap<>(values);
        for (String arg : keyValueArgs) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Override must have the form key=value, got: " + arg);
            }
            m.put(arg.substring(0, eq).trim(), arg.substring(eq + 1).trim());
        }
        return new ExperimentConfig(m);
    }

    public boolean has(String key) {
        String v = values.get(key);
        return v != null && !v.isEmpty();
    }

    public String getString(String key, String defaultValue) {
        return has(key) ? values.get(key) : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(values.get(key));
        } catch (NumberFormatException e) {
            throw invalid(key, "an integer");
        }
    }

    public long getLong(String key, long defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(values.get(key));
        } catch (NumberFormatException e) {
            throw invalid(key, "a long integer");
        }
    }

    public double getDouble(String key, double defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(values.get(key));
        } catch (NumberFormatException e) {
            throw invalid(key, "a number");
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        String v = values.get(key).toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("false")) {
            return Boolean.parseBoolean(v);
        }
        throw invalid(key, "true or false");
    }

    /** Comma-separated list; returns the default when the key is absent. */
    public List<String> getStringList(String key, List<String> defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        List<String> out = new ArrayList<>();
        for (String part : values.get(key).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public List<Long> getLongList(String key, List<Long> defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        List<Long> out = new ArrayList<>();
        for (String s : getStringList(key, List.of())) {
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                throw invalid(key, "a comma-separated list of integers");
            }
        }
        return out;
    }

    public List<Integer> getIntList(String key, List<Integer> defaultValue) {
        if (!has(key)) {
            return defaultValue;
        }
        List<Integer> out = new ArrayList<>();
        for (String s : getStringList(key, List.of())) {
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                throw invalid(key, "a comma-separated list of integers");
            }
        }
        return out;
    }

    public Map<String, String> asMap() {
        return values;
    }

    /** Writes the effective configuration next to the results so every run is traceable. */
    public void writeTo(Path file, String comment) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# " + comment + System.lineSeparator());
            for (Map.Entry<String, String> e : values.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + System.lineSeparator());
            }
        }
    }

    private IllegalArgumentException invalid(String key, String expected) {
        return new IllegalArgumentException(
                "Config key '" + key + "' must be " + expected + " but was '" + values.get(key) + "'");
    }
}
