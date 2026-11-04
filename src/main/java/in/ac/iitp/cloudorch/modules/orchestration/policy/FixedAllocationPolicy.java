package in.ac.iitp.cloudorch.modules.orchestration.policy;

import in.ac.iitp.cloudorch.framework.execution.ControlObservation;
import in.ac.iitp.cloudorch.framework.execution.ScalingDecision;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;

/** Static provisioning baseline: always the same number of workers. */
public final class FixedAllocationPolicy implements ScalingPolicy {

    private final int workers;

    public FixedAllocationPolicy(int workers) {
        if (workers < 1) {
            throw new IllegalArgumentException("fixed worker count must be >= 1");
        }
        this.workers = workers;
    }

    @Override
    public String name() {
        return "FIXED-" + workers;
    }

    @Override
    public int initialWorkers(int poolDefault) {
        return workers;
    }

    @Override
    public ScalingDecision decide(ControlObservation obs) {
        return ScalingDecision.withoutForecast(workers);
    }
}
