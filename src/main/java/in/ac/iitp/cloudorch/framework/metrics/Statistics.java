package in.ac.iitp.cloudorch.framework.metrics;

import java.util.Arrays;
import java.util.List;

/** Small descriptive-statistics helpers. NaN inputs are not allowed unless stated. */
public final class Statistics {

    private Statistics() {
    }

    public static double mean(double[] xs) {
        if (xs.length == 0) {
            return Double.NaN;
        }
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    /** Sample standard deviation (n - 1 denominator); NaN for fewer than two values. */
    public static double sampleStdDev(double[] xs) {
        if (xs.length < 2) {
            return Double.NaN;
        }
        double m = mean(xs);
        double ss = 0;
        for (double x : xs) {
            ss += (x - m) * (x - m);
        }
        return Math.sqrt(ss / (xs.length - 1));
    }

    public static double max(double[] xs) {
        return xs.length == 0 ? Double.NaN : Arrays.stream(xs).max().getAsDouble();
    }

    /**
     * Percentile by the nearest-rank method: the smallest value such that at least p % of the
     * data is less than or equal to it. {@code p} is in (0, 100].
     */
    public static double percentile(double[] xs, double p) {
        if (!(p > 0 && p <= 100)) {
            throw new IllegalArgumentException("p must be in (0, 100]");
        }
        if (xs.length == 0) {
            return Double.NaN;
        }
        double[] sorted = xs.clone();
        Arrays.sort(sorted);
        return percentileOfSorted(sorted, p);
    }

    public static double percentileOfSorted(double[] sorted, double p) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.length - 1e-9);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    /** Mean of the non-NaN values in the list; NaN if there are none. */
    public static double meanIgnoringNaN(List<Double> xs) {
        return mean(xs.stream().filter(v -> !Double.isNaN(v)).mapToDouble(Double::doubleValue).toArray());
    }

    /** Sample std of the non-NaN values in the list. */
    public static double stdIgnoringNaN(List<Double> xs) {
        return sampleStdDev(xs.stream().filter(v -> !Double.isNaN(v)).mapToDouble(Double::doubleValue).toArray());
    }
}
