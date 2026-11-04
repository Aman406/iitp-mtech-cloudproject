package in.ac.iitp.cloudorch.framework.execution;

import in.ac.iitp.cloudorch.framework.forecast.ForecastErrorTracker;

import java.util.List;

/**
 * Raw output of one simulation run. Per-task arrays are indexed like the workload trace; a task
 * that never started or finished has NaN start/end time.
 */
public record SimulationResult(
        String policyName,
        SimulationSettings settings,
        double workloadDurationSeconds,
        double endTime,
        double[] arrivalTimes,
        double[] startTimes,
        double[] endTimes,
        List<IntervalRecord> intervals,
        List<WorkerRecord> workers,
        double workerAliveSeconds,
        double workerReadySeconds,
        double workerBusySeconds,
        double queueLengthIntegral,
        int scaleOutActions,
        int scaleInActions,
        int workersLaunched,
        int peakAliveWorkers,
        ForecastErrorTracker forecastErrors) {
}
