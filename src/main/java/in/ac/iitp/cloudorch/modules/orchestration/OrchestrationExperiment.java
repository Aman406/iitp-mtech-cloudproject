package in.ac.iitp.cloudorch.modules.orchestration;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.execution.IntervalRecord;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;
import in.ac.iitp.cloudorch.framework.execution.SimulationEngine;
import in.ac.iitp.cloudorch.framework.execution.SimulationResult;
import in.ac.iitp.cloudorch.framework.execution.SimulationSettings;
import in.ac.iitp.cloudorch.framework.io.ConsoleTable;
import in.ac.iitp.cloudorch.framework.io.CsvWriter;
import in.ac.iitp.cloudorch.framework.metrics.MetricAggregator;
import in.ac.iitp.cloudorch.framework.metrics.SummaryMetrics;
import in.ac.iitp.cloudorch.framework.random.RandomSource;
import in.ac.iitp.cloudorch.framework.workload.WorkloadFactory;
import in.ac.iitp.cloudorch.framework.workload.WorkloadTrace;
import in.ac.iitp.cloudorch.modules.orchestration.policy.PolicyFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Research Module B — cost-aware predictive resource orchestration.
 *
 * <p>For each seed, one workload trace is generated and every configured scaling policy is run
 * on that same trace (common random numbers), so differences between policies are not caused by
 * different workloads. Results are written per run, aggregated over seeds, and as per-interval
 * time series for plotting.
 */
public final class OrchestrationExperiment {

    /** One (seed, policy) run. */
    public record Run(long seed, SummaryMetrics metrics) {
    }

    public record Report(List<Run> runs, MetricAggregator aggregate, Path outputDir) {
    }

    private static final List<String> INTERVAL_HEADER = List.of("policy", "interval", "start_s", "end_s",
            "arrivals", "forecast_arrivals", "completions", "target_workers", "committed_workers",
            "ready_workers", "provisioning_workers", "queue_length", "utilization", "accrued_worker_hours");

    private final ExperimentConfig config;
    private final PrintStream log;

    public OrchestrationExperiment(ExperimentConfig config, PrintStream log) {
        this.config = config;
        this.log = log;
    }

    public Report run() throws IOException {
        List<Long> seeds = config.getLongList("experiment.seeds", List.of(42L));
        Path out = Path.of(config.getString("output.dir", "results/orchestration"));
        List<String> policySpecs = config.getStringList("policies",
                List.of("FIXED", "REACTIVE", "PREDICTIVE", "PREDICTIVE_COST_AWARE"));
        boolean writeTasks = config.getBoolean("output.taskRecords", false);
        SimulationSettings settings = SimulationSettings.fromConfig(config);

        log.println("=== Module B: Cost-Aware Predictive Resource Orchestration ===");
        log.printf(Locale.ROOT, "experiment=%s seeds=%s policies=%s%n",
                config.getString("experiment.name", "orchestration"), seeds, policySpecs);
        log.printf(Locale.ROOT, "control interval=%.0fs pool=[min %d, max %d, initial %d] provisioning delay=%.0fs "
                        + "price=%.4f/worker-h billing=%.0fs blocks (min %.0fs) SLA=%.1fs%n",
                settings.controlIntervalSeconds(), settings.pool().minWorkers(), settings.pool().maxWorkers(),
                settings.pool().initialWorkers(), settings.pool().provisioningDelaySeconds(),
                settings.costModel().pricePerWorkerHour(), settings.costModel().billingGranularitySeconds(),
                settings.costModel().minimumBilledSeconds(), settings.slaLatencySeconds());

        List<Run> runs = new ArrayList<>();
        MetricAggregator agg = new MetricAggregator();
        for (long seed : seeds) {
            WorkloadTrace trace = WorkloadFactory.build(config, new RandomSource(seed).fork("workload"));
            log.printf(Locale.ROOT, "seed %d: %d tasks over %.0fs, mean service %.3fs (%s)%n",
                    seed, trace.size(), trace.durationSeconds(), trace.meanServiceTime(), trace.description());
            try (CsvWriter series = new CsvWriter(out.resolve("orchestration_intervals_seed" + seed + ".csv"), INTERVAL_HEADER)) {
                for (String spec : policySpecs) {
                    ScalingPolicy policy = PolicyFactory.create(spec, config, settings);
                    SimulationResult result = SimulationEngine.simulate(trace, policy, settings);
                    SummaryMetrics m = SummaryMetrics.of(result);
                    runs.add(new Run(seed, m));
                    agg.add(m.policy(), m.toMap());
                    writeIntervals(series, result);
                    if (writeTasks) {
                        writeTasks(out.resolve("tasks_seed" + seed + "_" + safe(m.policy()) + ".csv"), result);
                    }
                    log.printf(Locale.ROOT, "  %-28s p95 latency %8.2fs  worker-h %7.2f  cost %8.4f  incomplete %d%n",
                            m.policy(), m.p95LatencySeconds(), m.workerHours(), m.cost(), m.tasksIncomplete());
                }
            }
        }

        writeSummary(out.resolve("orchestration_summary.csv"), runs);
        writeAggregate(out.resolve("orchestration_aggregate.csv"), agg);
        config.writeTo(out.resolve("effective-config.properties"), "effective configuration for Module B run");
        printTable(agg);
        log.println("CSV written to " + out.toAbsolutePath().normalize());
        return new Report(List.copyOf(runs), agg, out);
    }

    private static void writeIntervals(CsvWriter w, SimulationResult r) throws IOException {
        for (IntervalRecord i : r.intervals()) {
            w.writeRow(List.of(r.policyName(), i.index(), i.startTime(), i.endTime(), i.arrivals(),
                    i.forecastArrivals(), i.completions(), i.targetWorkers(), i.committedWorkers(),
                    i.readyWorkers(), i.provisioningWorkers(), i.queueLength(), i.utilization(),
                    i.accruedWorkerSeconds() / 3600.0));
        }
    }

    private static void writeTasks(Path file, SimulationResult r) throws IOException {
        try (CsvWriter w = new CsvWriter(file, List.of("task", "arrival_s", "start_s", "end_s", "wait_s", "latency_s"))) {
            for (int i = 0; i < r.arrivalTimes().length; i++) {
                double a = r.arrivalTimes()[i];
                double s = r.startTimes()[i];
                double e = r.endTimes()[i];
                w.writeRow(List.of(i, a, s, e, s - a, e - a));
            }
        }
    }

    private static void writeSummary(Path file, List<Run> runs) throws IOException {
        List<String> header = new ArrayList<>(List.of("seed", "policy"));
        header.addAll(runs.get(0).metrics().toMap().keySet());
        try (CsvWriter w = new CsvWriter(file, header)) {
            for (Run r : runs) {
                List<Object> row = new ArrayList<>(List.of(r.seed(), r.metrics().policy()));
                row.addAll(r.metrics().toMap().values());
                w.writeRow(row);
            }
        }
    }

    private static void writeAggregate(Path file, MetricAggregator agg) throws IOException {
        List<String> metrics = agg.metrics(agg.groups().get(0));
        List<String> header = new ArrayList<>(List.of("policy", "runs"));
        for (String m : metrics) {
            header.add(m + "_mean");
            header.add(m + "_std");
        }
        try (CsvWriter w = new CsvWriter(file, header)) {
            for (String g : agg.groups()) {
                List<Object> row = new ArrayList<>(List.of(g, agg.runs(g)));
                for (String m : metrics) {
                    row.add(agg.mean(g, m));
                    row.add(agg.std(g, m));
                }
                w.writeRow(row);
            }
        }
    }

    private void printTable(MetricAggregator agg) {
        ConsoleTable t = new ConsoleTable(List.of("policy", "mean wait s", "p95 wait s", "p95 latency s",
                "SLA viol %", "thruput/s", "worker-h", "billed cost", "util", "forecast MAPE %"));
        for (String g : agg.groups()) {
            t.addRow(List.of(g,
                    pm(agg, g, "mean_wait_s"), pm(agg, g, "p95_wait_s"), pm(agg, g, "p95_latency_s"),
                    pm(agg, g, "sla_violation_pct"), pm(agg, g, "throughput_tasks_per_s"),
                    pm(agg, g, "worker_hours"), pm(agg, g, "cost"), pm(agg, g, "mean_utilization"),
                    pm(agg, g, "forecast_mape_pct")));
        }
        log.println();
        log.println("Mean +/- sample std over seeds (std omitted for a single seed; n/a = not applicable):");
        log.print(t.render());
    }

    private static String pm(MetricAggregator agg, String g, String metric) {
        double m = agg.mean(g, metric);
        double s = agg.std(g, metric);
        return Double.isNaN(s) ? fmt(m) : fmt(m) + " +/- " + fmt(s);
    }

    private static String fmt(double v) {
        if (Double.isNaN(v)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%.1f" : Math.abs(v) >= 1 ? "%.2f" : "%.3f", v);
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
