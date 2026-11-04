package in.ac.iitp.cloudorch.modules.orchestration.policy;

import in.ac.iitp.cloudorch.framework.execution.ControlObservation;
import in.ac.iitp.cloudorch.framework.execution.ScalingDecision;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;

/**
 * Reactive threshold (step) scaling, similar in spirit to rule-based autoscalers:
 * <ul>
 *   <li>scale out by {@code scaleOutStep} if utilization over the last interval is at least
 *       {@code upperUtilization} or the queue per ready worker is at least {@code queuePerWorkerThreshold};</li>
 *   <li>scale in by {@code scaleInStep} if utilization is at most {@code lowerUtilization} and the queue is empty;</li>
 *   <li>a cooldown (in control intervals) applies after each action of the same kind.</li>
 * </ul>
 * It only reacts to what has already happened, so capacity lags demand by at least one control
 * interval plus the provisioning delay.
 */
public final class ReactiveThresholdPolicy implements ScalingPolicy {

    private final double upperUtilization;
    private final double lowerUtilization;
    private final double queuePerWorkerThreshold;
    private final int scaleOutStep;
    private final int scaleInStep;
    private final int scaleOutCooldownIntervals;
    private final int scaleInCooldownIntervals;

    private int lastScaleOutInterval = Integer.MIN_VALUE / 2;
    private int lastScaleInInterval = Integer.MIN_VALUE / 2;

    public ReactiveThresholdPolicy(double upperUtilization, double lowerUtilization, double queuePerWorkerThreshold,
                                   int scaleOutStep, int scaleInStep,
                                   int scaleOutCooldownIntervals, int scaleInCooldownIntervals) {
        if (!(lowerUtilization >= 0 && lowerUtilization < upperUtilization && upperUtilization <= 1)) {
            throw new IllegalArgumentException("require 0 <= lowerUtilization < upperUtilization <= 1");
        }
        if (scaleOutStep < 1 || scaleInStep < 1 || scaleOutCooldownIntervals < 0 || scaleInCooldownIntervals < 0
                || queuePerWorkerThreshold <= 0) {
            throw new IllegalArgumentException("steps must be >= 1, cooldowns >= 0, queue threshold > 0");
        }
        this.upperUtilization = upperUtilization;
        this.lowerUtilization = lowerUtilization;
        this.queuePerWorkerThreshold = queuePerWorkerThreshold;
        this.scaleOutStep = scaleOutStep;
        this.scaleInStep = scaleInStep;
        this.scaleOutCooldownIntervals = scaleOutCooldownIntervals;
        this.scaleInCooldownIntervals = scaleInCooldownIntervals;
    }

    @Override
    public String name() {
        return "REACTIVE";
    }

    @Override
    public ScalingDecision decide(ControlObservation obs) {
        int current = obs.committedWorkers();
        int k = obs.intervalIndex();
        double queuePerWorker = obs.queueLength() / (double) Math.max(1, obs.readyWorkers());
        boolean overloaded = obs.utilizationLastInterval() >= upperUtilization
                || queuePerWorker >= queuePerWorkerThreshold;
        boolean underloaded = obs.utilizationLastInterval() <= lowerUtilization && obs.queueLength() == 0;

        if (overloaded && k - lastScaleOutInterval >= scaleOutCooldownIntervals && current < obs.maxWorkers()) {
            lastScaleOutInterval = k;
            return ScalingDecision.withoutForecast(current + scaleOutStep);
        }
        if (underloaded && k - lastScaleInInterval >= scaleInCooldownIntervals
                && k - lastScaleOutInterval >= scaleInCooldownIntervals && current > obs.minWorkers()) {
            lastScaleInInterval = k;
            return ScalingDecision.withoutForecast(current - scaleInStep);
        }
        return ScalingDecision.withoutForecast(current);
    }
}
