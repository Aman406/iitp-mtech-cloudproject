package in.ac.iitp.cloudorch.modules.forecasting;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.forecast.SimpleMovingAverageForecaster;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastingExperimentTest {

    @Test
    void walkForwardUsesOnlyPastObservations() {
        double[] series = {1, 2, 3, 4};
        double[] p = ForecastingExperiment.walkForward(new SimpleMovingAverageForecaster(2), series, 1);
        assertTrue(Double.isNaN(p[0]));
        assertEquals(1.0, p[1], 1e-12);
        assertEquals(1.5, p[2], 1e-12);
        assertEquals(2.5, p[3], 1e-12);

        // changing a future value must not change earlier predictions (no look-ahead leakage)
        double[] p2 = ForecastingExperiment.walkForward(new SimpleMovingAverageForecaster(2), new double[] {1, 2, 3, 999}, 1);
        assertEquals(p[3], p2[3], 1e-12);
    }

    @Test
    void multiStepHorizonShiftsForecasts() {
        double[] p = ForecastingExperiment.walkForward(new SimpleMovingAverageForecaster(1), new double[] {5, 6, 7, 8}, 2);
        assertTrue(Double.isNaN(p[0]) && Double.isNaN(p[1]));
        assertEquals(5.0, p[2], 1e-12);
        assertEquals(6.0, p[3], 1e-12);
    }

    @Test
    void endToEndRunWritesCsvAndIsReproducible() throws IOException {
        Path dir1 = Files.createTempDirectory("fc1");
        Path dir2 = Files.createTempDirectory("fc2");
        ForecastingExperiment.Report r1 = new ForecastingExperiment(config(dir1), quiet()).run();
        new ForecastingExperiment(config(dir2), quiet()).run();

        assertEquals(2 * 3 * 2, r1.rows().size()); // seeds x models x horizons
        assertEquals(4 + 2 - 1, r1.warmupIntervals());
        for (ForecastingExperiment.Row row : r1.rows()) {
            assertEquals(24 - r1.warmupIntervals(), row.points()); // 2 h / 300 s = 24 intervals
            assertTrue(row.rmse() >= row.mae());
        }
        for (String f : List.of("forecast_summary.csv", "forecast_aggregate.csv", "forecast_series_seed1.csv",
                "effective-config.properties")) {
            assertTrue(Files.exists(dir1.resolve(f)), f);
        }
        assertEquals(25, Files.readAllLines(dir1.resolve("forecast_series_seed1.csv")).size());
        assertEquals(Files.readString(dir1.resolve("forecast_summary.csv")),
                Files.readString(dir2.resolve("forecast_summary.csv")));
    }

    private static ExperimentConfig config(Path out) {
        Map<String, String> m = new HashMap<>();
        m.put("experiment.seeds", "1,2");
        m.put("output.dir", out.toString());
        m.put("workload.durationSeconds", "7200");
        m.put("workload.baseRate", "2");
        m.put("workload.diurnalAmplitude", "0.5");
        m.put("workload.diurnalPeriodSeconds", "3600");
        m.put("workload.burstsPerHour", "2");
        m.put("workload.burstMultiplier", "2");
        m.put("workload.burstDurationSeconds", "300");
        m.put("forecast.intervalSeconds", "300");
        m.put("forecast.models", "NAIVE,SMA:4,WMA:3");
        m.put("forecast.horizons", "1,2");
        return ExperimentConfig.of(m);
    }

    private static PrintStream quiet() {
        return new PrintStream(new ByteArrayOutputStream());
    }
}
