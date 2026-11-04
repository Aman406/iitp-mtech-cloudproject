package in.ac.iitp.cloudorch.framework.metrics;

import in.ac.iitp.cloudorch.framework.execution.SimulationResult;
import in.ac.iitp.cloudorch.framework.execution.WorkerRecord;
import in.ac.iitp.cloudorch.framework.cost.CostModel;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headline metrics of one simulation run. Definitions:
 * <ul>
 *   <li>queue wait = start time - arrival time (tasks that started)</li>
 *   <li>latency (response time) = completion time - arrival time (completed tasks)</li>
 *   <li>percentiles use the nearest-rank method</li>
 *   <li>SLA violation % = tasks with latency &gt; SLA, plus incomplete tasks, over all arrivals</li>
 *   <li>throughput = completed tasks / makespan, where makespan runs from t = 0 until the last
 *       task finishes (or the workload ends, whichever is later)</li>
 *   <li>worker-hours = total alive time of all workers, including provisioning time</li>
 *   <li>billed worker-hours / cost apply the {@link CostModel} rounding per worker</li>
 *   <li>utilization = busy worker-seconds / ready worker-seconds over the whole run</li>
 *   <li>forecast error = one-step-ahead error of the policy's arrival forecast (predictive policies only)</li>
 * </ul>
 */
public record SummaryMetrics(
        String policy,
        long tasksArrived,
        long tasksCompleted,
        long tasksIncomplete,
        double meanWaitSeconds,
        double p50WaitSeconds,
        double p95WaitSeconds,
        double p99WaitSeconds,
        double maxWaitSeconds,
        double meanLatencySeconds,
        double p95LatencySeconds,
        double p99LatencySeconds,
        double slaViolationPercent,
        double throughputTasksPerSecond,
        double makespanSeconds,
        double workerHours,
        double billedWorkerHours,
        double cost,
        double costPer1000Tasks,
        double meanUtilization,
        double meanQueueLength,
        int peakWorkers,
        int workersLaunched,
        int scaleOutActions,
        int scaleInActions,
        double forecastMae,
        double forecastRmse,
        double forecastMapePercent,
        long forecastPoints) {

    public static SummaryMetrics of(SimulationResult r) {
        double[] arr = r.arrivalTimes();
        double[] start = r.startTimes();
        double[] end = r.endTimes();
        int n = arr.length;
        double[] waits = new double[n];
        double[] lat = new double[n];
        int nw = 0;
        int nl = 0;
        long slaViolations = 0;
        double sla = r.settings().slaLatencySeconds();
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(start[i])) {
                waits[nw++] = start[i] - arr[i];
            }
            if (!Double.isNaN(end[i])) {
                double l = end[i] - arr[i];
                lat[nl++] = l;
                if (l > sla) {
                    slaViolations++;
                }
            } else {
                slaViolations++;
            }
        }
        waits = Arrays.copyOf(waits, nw);
        lat = Arrays.copyOf(lat, nl);
        Arrays.sort(waits);
        Arrays.sort(lat);

        CostModel cm = r.settings().costModel();
        double billedSeconds = 0;
        for (WorkerRecord w : r.workers()) {
            billedSeconds += cm.billedSeconds(w.lifetimeSeconds());
        }
        double cost = billedSeconds / 3600.0 * cm.pricePerWorkerHour();
        double makespan = r.endTime();

        return new SummaryMetrics(
                r.policyName(),
                n, nl, n - nl,
                Statistics.mean(waits),
                Statistics.percentileOfSorted(waits, 50),
                Statistics.percentileOfSorted(waits, 95),
                Statistics.percentileOfSorted(waits, 99),
                nw == 0 ? Double.NaN : waits[nw - 1],
                Statistics.mean(lat),
                Statistics.percentileOfSorted(lat, 95),
                Statistics.percentileOfSorted(lat, 99),
                n == 0 ? 0.0 : 100.0 * slaViolations / n,
                makespan > 0 ? nl / makespan : Double.NaN,
                makespan,
                r.workerAliveSeconds() / 3600.0,
                billedSeconds / 3600.0,
                cost,
                nl == 0 ? Double.NaN : cost / nl * 1000.0,
                r.workerReadySeconds() > 0 ? r.workerBusySeconds() / r.workerReadySeconds() : Double.NaN,
                makespan > 0 ? r.queueLengthIntegral() / makespan : Double.NaN,
                r.peakAliveWorkers(),
                r.workersLaunched(),
                r.scaleOutActions(),
                r.scaleInActions(),
                r.forecastErrors().mae(),
                r.forecastErrors().rmse(),
                r.forecastErrors().mapePercent(),
                r.forecastErrors().count());
    }

    /** Numeric metrics in a stable column order (the policy name is not included). */
    public Map<String, Double> toMap() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("tasks_arrived", (double) tasksArrived);
        m.put("tasks_completed", (double) tasksCompleted);
        m.put("tasks_incomplete", (double) tasksIncomplete);
        m.put("mean_wait_s", meanWaitSeconds);
        m.put("p50_wait_s", p50WaitSeconds);
        m.put("p95_wait_s", p95WaitSeconds);
        m.put("p99_wait_s", p99WaitSeconds);
        m.put("max_wait_s", maxWaitSeconds);
        m.put("mean_latency_s", meanLatencySeconds);
        m.put("p95_latency_s", p95LatencySeconds);
        m.put("p99_latency_s", p99LatencySeconds);
        m.put("sla_violation_pct", slaViolationPercent);
        m.put("throughput_tasks_per_s", throughputTasksPerSecond);
        m.put("makespan_s", makespanSeconds);
        m.put("worker_hours", workerHours);
        m.put("billed_worker_hours", billedWorkerHours);
        m.put("cost", cost);
        m.put("cost_per_1000_tasks", costPer1000Tasks);
        m.put("mean_utilization", meanUtilization);
        m.put("mean_queue_length", meanQueueLength);
        m.put("peak_workers", (double) peakWorkers);
        m.put("workers_launched", (double) workersLaunched);
        m.put("scale_out_actions", (double) scaleOutActions);
        m.put("scale_in_actions", (double) scaleInActions);
        m.put("forecast_mae", forecastMae);
        m.put("forecast_rmse", forecastRmse);
        m.put("forecast_mape_pct", forecastMapePercent);
        m.put("forecast_points", (double) forecastPoints);
        return m;
    }
}
