package in.ac.iitp.cloudorch.framework.execution;

/**
 * Time-series row describing one control interval, recorded at the interval's end after the
 * policy's decision has been applied.
 *
 * @param forecastArrivals the forecast for this interval that the policy made one interval earlier
 *                         (NaN if none)
 * @param targetWorkers    the pool size the engine applied (after clamping)
 * @param committedWorkers committed workers after applying the decision
 * @param readyWorkers     ready workers after applying the decision
 * @param provisioningWorkers workers still starting up after applying the decision
 * @param queueLength      tasks waiting at the interval's end
 * @param utilization      busy / ready worker-seconds during the interval
 * @param accruedWorkerSeconds cumulative worker-seconds (alive time) since t = 0
 */
public record IntervalRecord(
        int index,
        double startTime,
        double endTime,
        long arrivals,
        double forecastArrivals,
        long completions,
        int targetWorkers,
        int committedWorkers,
        int readyWorkers,
        int provisioningWorkers,
        int queueLength,
        double utilization,
        double accruedWorkerSeconds) {
}
