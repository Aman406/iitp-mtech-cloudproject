package in.ac.iitp.cloudorch.framework.workload;

import java.util.Arrays;

/**
 * An immutable, fully materialised workload: task arrival times (sorted, seconds from t=0) and
 * the service time each task needs. Generated once per seed and replayed for every scaling
 * policy so that policies are compared on exactly the same tasks.
 */
public final class WorkloadTrace {

    private final double[] arrivalTimes;
    private final double[] serviceTimes;
    private final double durationSeconds;
    private final String description;

    public WorkloadTrace(double[] arrivalTimes, double[] serviceTimes, double durationSeconds, String description) {
        if (arrivalTimes.length != serviceTimes.length) {
            throw new IllegalArgumentException("arrival and service arrays differ in length");
        }
        if (!(durationSeconds > 0)) {
            throw new IllegalArgumentException("durationSeconds must be > 0");
        }
        for (int i = 0; i < arrivalTimes.length; i++) {
            if (arrivalTimes[i] < 0 || arrivalTimes[i] >= durationSeconds) {
                throw new IllegalArgumentException("arrival " + i + " outside [0, duration)");
            }
            if (i > 0 && arrivalTimes[i] < arrivalTimes[i - 1]) {
                throw new IllegalArgumentException("arrival times must be sorted");
            }
            if (!(serviceTimes[i] > 0)) {
                throw new IllegalArgumentException("service time " + i + " must be > 0");
            }
        }
        this.arrivalTimes = arrivalTimes.clone();
        this.serviceTimes = serviceTimes.clone();
        this.durationSeconds = durationSeconds;
        this.description = description;
    }

    public int size() {
        return arrivalTimes.length;
    }

    public double arrivalTime(int i) {
        return arrivalTimes[i];
    }

    public double serviceTime(int i) {
        return serviceTimes[i];
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public String description() {
        return description;
    }

    public double meanServiceTime() {
        return Arrays.stream(serviceTimes).average().orElse(Double.NaN);
    }

    /** Number of arrivals in each consecutive interval [k*interval, (k+1)*interval). */
    public long[] arrivalCounts(double intervalSeconds) {
        if (!(intervalSeconds > 0)) {
            throw new IllegalArgumentException("intervalSeconds must be > 0");
        }
        int n = (int) Math.ceil(durationSeconds / intervalSeconds - 1e-9);
        long[] counts = new long[n];
        for (double t : arrivalTimes) {
            int k = Math.min(n - 1, (int) (t / intervalSeconds));
            counts[k]++;
        }
        return counts;
    }
}
