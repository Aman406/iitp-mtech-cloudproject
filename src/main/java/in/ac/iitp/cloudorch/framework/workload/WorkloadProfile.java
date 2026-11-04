package in.ac.iitp.cloudorch.framework.workload;

/**
 * Parameters of the synthetic, time-varying arrival-rate function:
 *
 * <pre>
 *   rate(t) = baseRate
 *           * max(0, 1 + trendPerHour * t / 3600)
 *           * (1 + diurnalAmplitude * sin(2*pi*t / diurnalPeriodSeconds + phase))
 *           * (burstMultiplier if t is inside a burst, else 1)
 * </pre>
 *
 * Arrivals are a non-homogeneous Poisson process with this rate. Burst start times are random:
 * at each whole minute a burst starts with probability {@code burstsPerHour / 60}.
 *
 * @param baseRateTasksPerSecond mean arrival rate before modulation (tasks/s)
 * @param diurnalAmplitude       relative amplitude of the periodic component, in [0, 1]
 * @param diurnalPeriodSeconds   period of the periodic component (e.g. 86400 for a day)
 * @param diurnalPhaseRadians    phase shift of the periodic component
 * @param trendPerHour           relative linear growth of the rate per hour (0.05 = +5 %/h)
 * @param burstsPerHour          expected number of burst starts per hour (0 disables bursts)
 * @param burstMultiplier        rate multiplier while a burst is active (>= 1)
 * @param burstDurationSeconds   length of each burst
 */
public record WorkloadProfile(
        double baseRateTasksPerSecond,
        double diurnalAmplitude,
        double diurnalPeriodSeconds,
        double diurnalPhaseRadians,
        double trendPerHour,
        double burstsPerHour,
        double burstMultiplier,
        double burstDurationSeconds) {

    public WorkloadProfile {
        if (!(baseRateTasksPerSecond > 0)) {
            throw new IllegalArgumentException("baseRateTasksPerSecond must be > 0");
        }
        if (diurnalAmplitude < 0 || diurnalAmplitude > 1) {
            throw new IllegalArgumentException("diurnalAmplitude must be in [0, 1]");
        }
        if (!(diurnalPeriodSeconds > 0)) {
            throw new IllegalArgumentException("diurnalPeriodSeconds must be > 0");
        }
        if (burstsPerHour < 0 || burstsPerHour > 60) {
            throw new IllegalArgumentException("burstsPerHour must be in [0, 60]");
        }
        if (burstMultiplier < 1) {
            throw new IllegalArgumentException("burstMultiplier must be >= 1");
        }
        if (burstDurationSeconds < 0) {
            throw new IllegalArgumentException("burstDurationSeconds must be >= 0");
        }
    }

    /** Constant-rate Poisson workload (useful for tests and calibration). */
    public static WorkloadProfile constant(double ratePerSecond) {
        return new WorkloadProfile(ratePerSecond, 0, 3600, 0, 0, 0, 1, 0);
    }

    /** Rate at time t, ignoring bursts. */
    public double baseShape(double t) {
        double trend = Math.max(0.0, 1.0 + trendPerHour * t / 3600.0);
        double periodic = 1.0 + diurnalAmplitude * StrictMath.sin(2 * Math.PI * t / diurnalPeriodSeconds + diurnalPhaseRadians);
        return baseRateTasksPerSecond * trend * periodic;
    }

    /** Upper bound of the rate over [0, horizon], used for Poisson thinning. */
    public double maxRate(double horizonSeconds) {
        double trend = Math.max(1.0, 1.0 + trendPerHour * horizonSeconds / 3600.0);
        double bursts = burstsPerHour > 0 ? burstMultiplier : 1.0;
        return baseRateTasksPerSecond * trend * (1.0 + diurnalAmplitude) * bursts;
    }
}
