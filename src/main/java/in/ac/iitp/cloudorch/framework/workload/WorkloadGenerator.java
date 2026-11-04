package in.ac.iitp.cloudorch.framework.workload;

import in.ac.iitp.cloudorch.framework.random.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Generates synthetic workload traces from a {@link WorkloadProfile}. */
public final class WorkloadGenerator {

    private WorkloadGenerator() {
    }

    /**
     * Generates a non-homogeneous Poisson arrival process using Lewis–Shedler thinning, and draws
     * one service time per task. Arrivals, burst times and service times use independent random
     * streams derived from {@code rng}.
     */
    public static WorkloadTrace generate(WorkloadProfile profile, ServiceTimeDistribution service,
                                         double durationSeconds, RandomSource rng) {
        if (!(durationSeconds > 0)) {
            throw new IllegalArgumentException("durationSeconds must be > 0");
        }
        RandomSource arrivalRng = rng.fork("arrivals");
        RandomSource burstRng = rng.fork("bursts");
        RandomSource serviceRng = rng.fork("service");

        List<double[]> bursts = burstWindows(profile, durationSeconds, burstRng);
        double lambdaMax = profile.maxRate(durationSeconds);

        double[] arrivals = new double[Math.max(16, (int) (profile.baseRateTasksPerSecond() * durationSeconds * 1.2))];
        int n = 0;
        double t = 0;
        while (true) {
            t += arrivalRng.exponential(1.0 / lambdaMax);
            if (t >= durationSeconds) {
                break;
            }
            double rate = rateAt(profile, bursts, t);
            if (arrivalRng.nextDouble() * lambdaMax < rate) {
                if (n == arrivals.length) {
                    arrivals = Arrays.copyOf(arrivals, arrivals.length * 2);
                }
                arrivals[n++] = t;
            }
        }
        arrivals = Arrays.copyOf(arrivals, n);
        double[] services = new double[n];
        for (int i = 0; i < n; i++) {
            services[i] = service.sample(serviceRng);
        }
        String desc = String.format(Locale.ROOT,
                "synthetic: base=%.3f/s amp=%.2f period=%.0fs trend=%.3f/h bursts=%.2f/h x%.2f for %.0fs; service=%s; %d burst(s)",
                profile.baseRateTasksPerSecond(), profile.diurnalAmplitude(), profile.diurnalPeriodSeconds(),
                profile.trendPerHour(), profile.burstsPerHour(), profile.burstMultiplier(),
                profile.burstDurationSeconds(), service, bursts.size());
        return new WorkloadTrace(arrivals, services, durationSeconds, desc);
    }

    /**
     * Builds a trace from per-interval arrival counts (for example, counts aggregated from a real
     * cluster trace). Arrivals are spread uniformly at random inside each interval.
     */
    public static WorkloadTrace fromIntervalCounts(long[] counts, double intervalSeconds,
                                                   ServiceTimeDistribution service, RandomSource rng) {
        if (counts.length == 0) {
            throw new IllegalArgumentException("counts must not be empty");
        }
        RandomSource arrivalRng = rng.fork("arrivals");
        RandomSource serviceRng = rng.fork("service");
        long total = Arrays.stream(counts).sum();
        if (total > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("trace too large");
        }
        double[] arrivals = new double[(int) total];
        int idx = 0;
        for (int k = 0; k < counts.length; k++) {
            if (counts[k] < 0) {
                throw new IllegalArgumentException("negative count at interval " + k);
            }
            int start = idx;
            for (long j = 0; j < counts[k]; j++) {
                arrivals[idx++] = k * intervalSeconds + arrivalRng.nextDouble() * intervalSeconds;
            }
            Arrays.sort(arrivals, start, idx);
        }
        double[] services = new double[arrivals.length];
        for (int i = 0; i < services.length; i++) {
            services[i] = service.sample(serviceRng);
        }
        return new WorkloadTrace(arrivals, services, counts.length * intervalSeconds,
                "replayed interval counts (" + counts.length + " intervals of " + intervalSeconds + "s); service=" + service);
    }

    /** Arrival rate at time t including active bursts. */
    static double rateAt(WorkloadProfile profile, List<double[]> bursts, double t) {
        double rate = profile.baseShape(t);
        for (double[] b : bursts) {
            if (t >= b[0] && t < b[1]) {
                return rate * profile.burstMultiplier();
            }
        }
        return rate;
    }

    static List<double[]> burstWindows(WorkloadProfile profile, double durationSeconds, RandomSource burstRng) {
        List<double[]> bursts = new ArrayList<>();
        if (profile.burstsPerHour() <= 0 || profile.burstDurationSeconds() <= 0) {
            return bursts;
        }
        double pPerMinute = profile.burstsPerHour() / 60.0;
        for (int minute = 0; minute * 60.0 < durationSeconds; minute++) {
            if (burstRng.nextDouble() < pPerMinute) {
                double start = minute * 60.0;
                bursts.add(new double[] {start, start + profile.burstDurationSeconds()});
            }
        }
        return bursts;
    }
}
