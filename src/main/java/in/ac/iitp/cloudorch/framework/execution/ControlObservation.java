package in.ac.iitp.cloudorch.framework.execution;

/**
 * What a scaling policy can observe at the end of each control interval. Policies see only
 * monitoring data that a real autoscaler could collect; they never see future arrivals.
 *
 * @param time                     current simulation time (end of the interval)
 * @param intervalIndex            0-based index of the interval that just ended
 * @param intervalSeconds          control interval length
 * @param arrivalsLastInterval     tasks that arrived during the interval
 * @param completionsLastInterval  tasks that finished during the interval
 * @param queueLength              tasks waiting (not yet started) right now
 * @param committedWorkers         workers the pool is committed to: provisioning + ready, excluding
 *                                 workers already marked for removal
 * @param readyWorkers             workers able to run tasks now (idle or busy, including ones
 *                                 marked for removal that are still running)
 * @param provisioningWorkers      workers launched but not yet ready
 * @param busyWorkers              workers currently executing a task
 * @param utilizationLastInterval  busy worker-seconds / ready worker-seconds during the interval
 * @param meanServiceTimeEstimate  mean service time of all tasks completed so far (NaN if none)
 * @param minWorkers               pool lower bound
 * @param maxWorkers               pool upper bound
 */
public record ControlObservation(
        double time,
        int intervalIndex,
        double intervalSeconds,
        long arrivalsLastInterval,
        long completionsLastInterval,
        int queueLength,
        int committedWorkers,
        int readyWorkers,
        int provisioningWorkers,
        int busyWorkers,
        double utilizationLastInterval,
        double meanServiceTimeEstimate,
        int minWorkers,
        int maxWorkers) {
}
