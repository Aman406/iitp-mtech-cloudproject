package in.ac.iitp.cloudorch.framework.execution;

/**
 * Extension point for research modules: decides the worker-pool size at each control interval.
 * Implementations may keep state across calls; the engine creates/uses one instance per run.
 */
public interface ScalingPolicy {

    String name();

    ScalingDecision decide(ControlObservation observation);

    /**
     * If true, scale-in does not terminate workers immediately. Each worker selected for removal
     * keeps serving tasks until the end of its already-paid billing block and is then terminated,
     * unless a later scale-out reclaims it first. Workers closest to their billing boundary are
     * removed first.
     */
    default boolean costAwareScaleIn() {
        return false;
    }

    /**
     * Workers running (pre-warmed) at t = 0. Defaults to the pool's configured initial size;
     * a fixed-allocation policy overrides this so it starts at its own size.
     */
    default int initialWorkers(int poolDefault) {
        return poolDefault;
    }
}
