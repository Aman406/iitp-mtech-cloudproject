package in.ac.iitp.cloudorch.framework.execution;

/**
 * Lifecycle of one worker after the run.
 *
 * @param readyTime NaN if the worker was cancelled while still provisioning
 */
public record WorkerRecord(int id, double launchTime, double readyTime, double terminateTime,
                           long tasksCompleted, double busySeconds) {

    public double lifetimeSeconds() {
        return terminateTime - launchTime;
    }
}
