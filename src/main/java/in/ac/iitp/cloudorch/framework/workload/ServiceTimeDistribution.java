package in.ac.iitp.cloudorch.framework.workload;

import in.ac.iitp.cloudorch.framework.random.RandomSource;

import java.util.Locale;

/** Distribution of task execution (service) times on a single worker, in seconds. */
public record ServiceTimeDistribution(Type type, double meanSeconds, double cv) {

    /** Smallest service time ever produced, to avoid zero-length tasks. */
    public static final double MIN_SERVICE_SECONDS = 0.001;

    public enum Type { CONSTANT, EXPONENTIAL, LOGNORMAL }

    public ServiceTimeDistribution {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (!(meanSeconds > 0)) {
            throw new IllegalArgumentException("meanSeconds must be > 0");
        }
        if (cv < 0) {
            throw new IllegalArgumentException("cv must be >= 0");
        }
    }

    public static ServiceTimeDistribution parse(String type, double meanSeconds, double cv) {
        return new ServiceTimeDistribution(Type.valueOf(type.trim().toUpperCase(Locale.ROOT)), meanSeconds, cv);
    }

    public double sample(RandomSource rng) {
        double v = switch (type) {
            case CONSTANT -> meanSeconds;
            case EXPONENTIAL -> rng.exponential(meanSeconds);
            case LOGNORMAL -> rng.lognormal(meanSeconds, cv);
        };
        return Math.max(MIN_SERVICE_SECONDS, v);
    }

    @Override
    public String toString() {
        return type == Type.LOGNORMAL
                ? String.format(Locale.ROOT, "LOGNORMAL(mean=%.3fs, cv=%.2f)", meanSeconds, cv)
                : String.format(Locale.ROOT, "%s(mean=%.3fs)", type, meanSeconds);
    }
}
