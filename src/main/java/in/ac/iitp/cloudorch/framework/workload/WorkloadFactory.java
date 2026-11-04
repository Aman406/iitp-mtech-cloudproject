package in.ac.iitp.cloudorch.framework.workload;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.random.RandomSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Builds a {@link WorkloadTrace} from {@code workload.*} configuration keys. Shared by both research
 * modules so that they interpret workload parameters identically.
 *
 * <p>If {@code workload.traceFile} is set, per-interval counts are loaded from that CSV and
 * replayed; otherwise a synthetic trace is generated from the profile keys.
 */
public final class WorkloadFactory {

    private WorkloadFactory() {
    }

    public static WorkloadProfile profile(ExperimentConfig c) {
        return new WorkloadProfile(
                c.getDouble("workload.baseRate", 4.0),
                c.getDouble("workload.diurnalAmplitude", 0.5),
                c.getDouble("workload.diurnalPeriodSeconds", 3600),
                c.getDouble("workload.diurnalPhase", 0.0),
                c.getDouble("workload.trendPerHour", 0.0),
                c.getDouble("workload.burstsPerHour", 0.0),
                c.getDouble("workload.burstMultiplier", 1.0),
                c.getDouble("workload.burstDurationSeconds", 0.0));
    }

    public static ServiceTimeDistribution service(ExperimentConfig c) {
        return ServiceTimeDistribution.parse(
                c.getString("workload.service.type", "LOGNORMAL"),
                c.getDouble("workload.service.meanSeconds", 2.0),
                c.getDouble("workload.service.cv", 0.5));
    }

    public static WorkloadTrace build(ExperimentConfig c, RandomSource rng) {
        ServiceTimeDistribution service = service(c);
        if (c.has("workload.traceFile")) {
            Path file = Path.of(c.getString("workload.traceFile", ""));
            try {
                long[] counts = TraceLoader.loadCounts(file, c.getString("workload.traceColumn", "arrivals"));
                return WorkloadGenerator.fromIntervalCounts(counts,
                        c.getDouble("workload.traceIntervalSeconds", 60), service, rng);
            } catch (IOException e) {
                throw new UncheckedIOException("could not load workload.traceFile " + file, e);
            }
        }
        return WorkloadGenerator.generate(profile(c), service, c.getDouble("workload.durationSeconds", 7200), rng);
    }
}
