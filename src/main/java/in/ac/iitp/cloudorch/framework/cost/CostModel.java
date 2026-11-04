package in.ac.iitp.cloudorch.framework.cost;

/**
 * Simulated on-demand pricing for worker instances.
 *
 * <p>A worker is billed from launch (including provisioning time) to termination. Billed time is
 * rounded up to whole {@code billingGranularitySeconds} blocks and is never less than
 * {@code minimumBilledSeconds}. For example, granularity 1 s with a 60 s minimum resembles
 * per-second billing, and granularity 3600 s resembles legacy per-hour billing.
 *
 * <p>Prices are in an abstract currency unit; they are illustrative parameters, not quotes from
 * any cloud provider.
 */
public record CostModel(double pricePerWorkerHour, double billingGranularitySeconds, double minimumBilledSeconds) {

    private static final double EPS = 1e-9;

    public CostModel {
        if (pricePerWorkerHour < 0) {
            throw new IllegalArgumentException("pricePerWorkerHour must be >= 0");
        }
        if (!(billingGranularitySeconds > 0)) {
            throw new IllegalArgumentException("billingGranularitySeconds must be > 0");
        }
        if (minimumBilledSeconds < 0) {
            throw new IllegalArgumentException("minimumBilledSeconds must be >= 0");
        }
    }

    /** Seconds charged for a worker that was alive for {@code lifetimeSeconds}. */
    public double billedSeconds(double lifetimeSeconds) {
        double blocks = Math.ceil(Math.max(0, lifetimeSeconds) / billingGranularitySeconds - EPS);
        return Math.max(minimumBilledSeconds, blocks * billingGranularitySeconds);
    }

    public double cost(double lifetimeSeconds) {
        return billedSeconds(lifetimeSeconds) / 3600.0 * pricePerWorkerHour;
    }

    /**
     * The earliest time at or after {@code now} at which terminating a worker launched at
     * {@code launchTime} wastes no already-paid time: the end of its current billing block
     * (and not before the minimum billed duration has elapsed).
     */
    public double nextBillingBoundary(double launchTime, double now) {
        double age = now - launchTime;
        double paidUntil = launchTime + billedSeconds(age);
        return paidUntil < now + EPS ? now : paidUntil;
    }
}
