package in.ac.iitp.cloudorch.framework.forecast;

import java.util.Locale;

/**
 * Creates forecasters from compact text specs used in configuration files:
 * {@code NAIVE}, {@code SMA:5}, {@code WMA:6}, {@code EMA:0.3}.
 */
public final class ForecasterFactory {

    private ForecasterFactory() {
    }

    public static Forecaster create(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("forecaster spec is empty");
        }
        String[] parts = spec.trim().split(":");
        String kind = parts[0].trim().toUpperCase(Locale.ROOT);
        try {
            return switch (kind) {
                case "NAIVE" -> {
                    requireArgs(parts, 1, spec);
                    yield new NaiveForecaster();
                }
                case "SMA" -> {
                    requireArgs(parts, 2, spec);
                    yield new SimpleMovingAverageForecaster(Integer.parseInt(parts[1].trim()));
                }
                case "WMA" -> {
                    requireArgs(parts, 2, spec);
                    yield new WeightedMovingAverageForecaster(Integer.parseInt(parts[1].trim()));
                }
                case "EMA" -> {
                    requireArgs(parts, 2, spec);
                    yield new ExponentialMovingAverageForecaster(Double.parseDouble(parts[1].trim()));
                }
                default -> throw new IllegalArgumentException(
                        "unknown forecaster '" + spec + "'; expected NAIVE, SMA:<w>, WMA:<w> or EMA:<alpha>");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad parameter in forecaster spec '" + spec + "'", e);
        }
    }

    /** Observations a forecaster needs before its window is full (1 for NAIVE and EMA). */
    public static int windowOf(Forecaster f) {
        return f instanceof WindowedForecaster w ? w.window() : 1;
    }

    private static void requireArgs(String[] parts, int n, String spec) {
        if (parts.length != n) {
            throw new IllegalArgumentException("forecaster spec '" + spec + "' has the wrong number of parameters");
        }
    }
}
