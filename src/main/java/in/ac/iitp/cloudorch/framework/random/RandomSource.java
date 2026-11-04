package in.ac.iitp.cloudorch.framework.random;

import java.util.SplittableRandom;

/**
 * Seeded pseudo-random source used by every stochastic component of the simulator.
 *
 * <p>All randomness flows from one experiment seed. Independent streams (arrivals, bursts,
 * service times, ...) are derived deterministically with {@link #fork(String)}, so changing how
 * many numbers one component draws never shifts the numbers another component sees. This keeps
 * experiments reproducible and lets different scaling policies be compared on an identical
 * workload (common random numbers).
 */
public final class RandomSource {

    private final long seed;
    private final SplittableRandom rng;

    public RandomSource(long seed) {
        this.seed = seed;
        this.rng = new SplittableRandom(seed);
    }

    public long seed() {
        return seed;
    }

    /** Derives an independent, reproducible stream identified by name. */
    public RandomSource fork(String streamName) {
        long h = seed;
        for (int i = 0; i < streamName.length(); i++) {
            h = mix64(h ^ streamName.charAt(i));
        }
        return new RandomSource(mix64(h + 0x9E3779B97F4A7C15L));
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return rng.nextDouble();
    }

    public int nextInt(int bound) {
        return rng.nextInt(bound);
    }

    /** Standard normal variate. */
    public double nextGaussian() {
        return rng.nextGaussian();
    }

    /** Exponential variate with the given mean. */
    public double exponential(double mean) {
        if (mean <= 0) {
            throw new IllegalArgumentException("mean must be > 0");
        }
        return -mean * StrictMath.log(1.0 - rng.nextDouble());
    }

    /**
     * Log-normal variate parameterised by its mean and coefficient of variation (std/mean),
     * which is more intuitive for service times than the underlying (mu, sigma).
     */
    public double lognormal(double mean, double cv) {
        if (mean <= 0 || cv < 0) {
            throw new IllegalArgumentException("mean must be > 0 and cv >= 0");
        }
        double sigma2 = StrictMath.log(1.0 + cv * cv);
        double mu = StrictMath.log(mean) - sigma2 / 2.0;
        return StrictMath.exp(mu + StrictMath.sqrt(sigma2) * rng.nextGaussian());
    }

    /** Stafford variant 13 of the SplitMix64 finaliser. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
