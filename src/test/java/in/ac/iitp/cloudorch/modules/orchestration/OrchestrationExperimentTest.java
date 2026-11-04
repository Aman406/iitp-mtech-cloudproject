package in.ac.iitp.cloudorch.modules.orchestration;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.metrics.SummaryMetrics;
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

class OrchestrationExperimentTest {

    @Test
    void allPoliciesRunOnTheSameWorkloadAndResultsAreReproducible() throws IOException {
        Path dir1 = Files.createTempDirectory("orch1");
        Path dir2 = Files.createTempDirectory("orch2");
        OrchestrationExperiment.Report r = new OrchestrationExperiment(config(dir1), quiet()).run();
        new OrchestrationExperiment(config(dir2), quiet()).run();

        assertEquals(2 * 4, r.runs().size());
        for (long seed : List.of(3L, 4L)) {
            long arrived = -1;
            for (OrchestrationExperiment.Run run : r.runs()) {
                if (run.seed() != seed) {
                    continue;
                }
                SummaryMetrics m = run.metrics();
                if (arrived < 0) {
                    arrived = m.tasksArrived();
                }
                assertEquals(arrived, m.tasksArrived(), "common random numbers: same tasks for every policy");
                assertEquals(0, m.tasksIncomplete());
                assertTrue(m.p95LatencySeconds() >= m.p95WaitSeconds());
                assertTrue(m.billedWorkerHours() >= m.workerHours() - 1e-9);
            }
        }
        for (String f : List.of("orchestration_summary.csv", "orchestration_aggregate.csv",
                "orchestration_intervals_seed3.csv", "effective-config.properties")) {
            assertTrue(Files.exists(dir1.resolve(f)), f);
        }
        assertEquals(Files.readString(dir1.resolve("orchestration_summary.csv")),
                Files.readString(dir2.resolve("orchestration_summary.csv")));
        assertEquals(Files.readString(dir1.resolve("orchestration_intervals_seed4.csv")),
                Files.readString(dir2.resolve("orchestration_intervals_seed4.csv")));
    }

    @Test
    void predictivePoliciesReportForecastErrorAndOthersDoNot() throws IOException {
        OrchestrationExperiment.Report r = new OrchestrationExperiment(config(Files.createTempDirectory("orch3")), quiet()).run();
        for (OrchestrationExperiment.Run run : r.runs()) {
            boolean predictive = run.metrics().policy().startsWith("PREDICTIVE");
            assertEquals(predictive, run.metrics().forecastPoints() > 0, run.metrics().policy());
        }
    }

    private static ExperimentConfig config(Path out) {
        Map<String, String> m = new HashMap<>();
        m.put("experiment.seeds", "3,4");
        m.put("output.dir", out.toString());
        m.put("policies", "FIXED:6,REACTIVE,PREDICTIVE,PREDICTIVE_COST_AWARE");
        m.put("workload.durationSeconds", "1800");
        m.put("workload.baseRate", "2");
        m.put("workload.diurnalAmplitude", "0.5");
        m.put("workload.diurnalPeriodSeconds", "900");
        m.put("workload.burstsPerHour", "4");
        m.put("workload.burstMultiplier", "2");
        m.put("workload.burstDurationSeconds", "120");
        m.put("pool.initialWorkers", "4");
        m.put("pool.maxWorkers", "20");
        m.put("pool.provisioningDelaySeconds", "30");
        m.put("cost.billingGranularitySeconds", "300");
        m.put("cost.minimumBilledSeconds", "300");
        return ExperimentConfig.of(m);
    }

    private static PrintStream quiet() {
        return new PrintStream(new ByteArrayOutputStream());
    }
}
