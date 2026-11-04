package in.ac.iitp.cloudorch.modules.forecasting;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.forecast.ForecastErrorTracker;
import in.ac.iitp.cloudorch.framework.forecast.Forecaster;
import in.ac.iitp.cloudorch.framework.forecast.ForecasterFactory;
import in.ac.iitp.cloudorch.framework.io.ConsoleTable;
import in.ac.iitp.cloudorch.framework.io.CsvWriter;
import in.ac.iitp.cloudorch.framework.metrics.MetricAggregator;
import in.ac.iitp.cloudorch.framework.random.RandomSource;
import in.ac.iitp.cloudorch.framework.workload.WorkloadFactory;
import in.ac.iitp.cloudorch.framework.workload.WorkloadTrace;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Research Module A — workload forecasting.
 *
 * <p>For each seed, generates (or loads) a workload, aggregates it into per-interval arrival
 * counts, and replays the series through each configured forecaster in a walk-forward
 * (rolling-origin) fashion: the forecast for interval k is made using only intervals before it.
 * Errors are computed over the same evaluation window for every model, after a warm-up long
 * enough to fill the largest window.
 */
public final class ForecastingExperiment {

    /** One evaluated (seed, model, horizon) combination. */
    public record Row(long seed, String model, int horizon, long points, double mae, double rmse,
                      double mapePercent, double smapePercent, double bias) {
    }

    /** Result returned to callers and tests. */
    public record Report(List<Row> rows, MetricAggregator aggregate, Path outputDir, int warmupIntervals) {
    }

    private final ExperimentConfig config;
    private final PrintStream log;

    public ForecastingExperiment(ExperimentConfig config, PrintStream log) {
        this.config = config;
        this.log = log;
    }

    public Report run() throws IOException {
        List<Long> seeds = config.getLongList("experiment.seeds", List.of(42L));
        Path out = Path.of(config.getString("output.dir", "results/forecasting"));
        double interval = config.getDouble("forecast.intervalSeconds", 300);
        List<String> specs = config.getStringList("forecast.models",
                List.of("NAIVE", "SMA:3", "SMA:6", "SMA:12", "WMA:6", "EMA:0.30"));
        List<Integer> horizons = config.getIntList("forecast.horizons", List.of(1));
        boolean writeSeries = config.getBoolean("output.series", true);

        for (int h : horizons) {
            if (h < 1) {
                throw new IllegalArgumentException("forecast.horizons must be >= 1");
            }
        }
        int maxWindow = 1;
        for (String s : specs) {
            maxWindow = Math.max(maxWindow, ForecasterFactory.windowOf(ForecasterFactory.create(s)));
        }
        int maxHorizon = horizons.stream().mapToInt(Integer::intValue).max().orElse(1);
        int autoWarmup = maxWindow + maxHorizon - 1;
        int warmup = config.getInt("forecast.warmupIntervals", autoWarmup);
        if (warmup < autoWarmup) {
            log.printf(Locale.ROOT, "WARNING: forecast.warmupIntervals=%d is shorter than the largest window + horizon - 1 (%d); "
                    + "early points use partially filled windows.%n", warmup, autoWarmup);
        }

        log.println("=== Module A: Workload Forecasting ===");
        log.printf(Locale.ROOT, "experiment=%s seeds=%s interval=%.0fs models=%s horizons=%s warmup=%d%n",
                config.getString("experiment.name", "forecasting"), seeds, interval, specs, horizons, warmup);

        List<Row> rows = new ArrayList<>();
        MetricAggregator agg = new MetricAggregator();
        for (long seed : seeds) {
            WorkloadTrace trace = WorkloadFactory.build(config, new RandomSource(seed).fork("workload"));
            long[] counts = trace.arrivalCounts(interval);
            double[] series = Arrays.stream(counts).asDoubleStream().toArray();
            if (series.length <= warmup) {
                throw new IllegalArgumentException("series has " + series.length
                        + " intervals, not more than the warm-up of " + warmup + "; increase workload duration");
            }
            log.printf(Locale.ROOT, "seed %d: %d tasks, %d intervals, mean %.1f arrivals/interval (%s)%n",
                    seed, trace.size(), series.length, Arrays.stream(series).average().orElse(0), trace.description());

            Map<String, double[]> predictionColumns = new LinkedHashMap<>();
            for (String spec : specs) {
                for (int h : horizons) {
                    Forecaster f = ForecasterFactory.create(spec);
                    double[] preds = walkForward(f, series, h);
                    ForecastErrorTracker t = new ForecastErrorTracker();
                    for (int k = warmup; k < series.length; k++) {
                        t.add(series[k], preds[k]);
                    }
                    Row row = new Row(seed, f.name(), h, t.count(), t.mae(), t.rmse(), t.mapePercent(), t.smapePercent(), t.bias());
                    rows.add(row);
                    Map<String, Double> m = new LinkedHashMap<>();
                    m.put("mae", row.mae());
                    m.put("rmse", row.rmse());
                    m.put("mape_pct", row.mapePercent());
                    m.put("smape_pct", row.smapePercent());
                    m.put("bias", row.bias());
                    agg.add(f.name() + "@h" + h, m);
                    predictionColumns.put(f.name() + "@h" + h, preds);
                }
            }
            if (writeSeries) {
                writeSeries(out.resolve("forecast_series_seed" + seed + ".csv"), series, interval, warmup, predictionColumns);
            }
        }

        writeSummary(out.resolve("forecast_summary.csv"), rows);
        writeAggregate(out.resolve("forecast_aggregate.csv"), agg, seeds.size());
        config.writeTo(out.resolve("effective-config.properties"), "effective configuration for Module A run");
        printTable(agg, horizons);
        log.println("CSV written to " + out.toAbsolutePath().normalize());
        return new Report(List.copyOf(rows), agg, out, warmup);
    }

    /**
     * preds[k] is the forecast of series[k] made after observing series[0 .. k-h]; NaN where no
     * forecast exists (the first h points).
     */
    static double[] walkForward(Forecaster f, double[] series, int horizon) {
        double[] preds = new double[series.length];
        Arrays.fill(preds, Double.NaN);
        for (int j = 0; j < series.length; j++) {
            f.observe(series[j]);
            if (j + horizon < series.length) {
                preds[j + horizon] = f.forecast(horizon);
            }
        }
        return preds;
    }

    private void writeSeries(Path file, double[] series, double interval, int warmup,
                             Map<String, double[]> predictions) throws IOException {
        List<String> header = new ArrayList<>(List.of("interval", "start_s", "in_evaluation", "actual"));
        header.addAll(predictions.keySet());
        try (CsvWriter w = new CsvWriter(file, header)) {
            for (int k = 0; k < series.length; k++) {
                List<Object> row = new ArrayList<>(List.of(k, k * interval, k >= warmup ? 1 : 0, series[k]));
                for (double[] p : predictions.values()) {
                    row.add(p[k]);
                }
                w.writeRow(row);
            }
        }
    }

    private static void writeSummary(Path file, List<Row> rows) throws IOException {
        try (CsvWriter w = new CsvWriter(file, List.of("seed", "model", "horizon", "points",
                "mae", "rmse", "mape_pct", "smape_pct", "bias"))) {
            for (Row r : rows) {
                w.writeRow(List.of(r.seed(), r.model(), r.horizon(), r.points(), r.mae(), r.rmse(),
                        r.mapePercent(), r.smapePercent(), r.bias()));
            }
        }
    }

    private static void writeAggregate(Path file, MetricAggregator agg, int seeds) throws IOException {
        List<String> metrics = List.of("mae", "rmse", "mape_pct", "smape_pct", "bias");
        List<String> header = new ArrayList<>(List.of("model", "horizon", "seeds"));
        for (String m : metrics) {
            header.add(m + "_mean");
            header.add(m + "_std");
        }
        try (CsvWriter w = new CsvWriter(file, header)) {
            for (String g : agg.groups()) {
                String[] parts = g.split("@h");
                List<Object> row = new ArrayList<>(List.of(parts[0], Integer.parseInt(parts[1]), seeds));
                for (String m : metrics) {
                    row.add(agg.mean(g, m));
                    row.add(agg.std(g, m));
                }
                w.writeRow(row);
            }
        }
    }

    private void printTable(MetricAggregator agg, List<Integer> horizons) {
        for (int h : horizons) {
            List<String> groups = new ArrayList<>();
            for (String g : agg.groups()) {
                if (g.endsWith("@h" + h)) {
                    groups.add(g);
                }
            }
            groups.sort(Comparator.comparingDouble(g -> agg.mean(g, "rmse")));
            ConsoleTable t = new ConsoleTable(List.of("model (h=" + h + ")", "MAE", "RMSE", "MAPE %", "sMAPE %", "bias"));
            for (String g : groups) {
                t.addRow(List.of(g.substring(0, g.indexOf("@h")),
                        pm(agg, g, "mae"), pm(agg, g, "rmse"), pm(agg, g, "mape_pct"),
                        pm(agg, g, "smape_pct"), pm(agg, g, "bias")));
            }
            log.println();
            log.println("Forecast error, mean +/- sample std over seeds, sorted by RMSE (horizon " + h + " interval(s)):");
            log.print(t.render());
        }
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
        return String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%.1f" : "%.2f", v);
    }
}
