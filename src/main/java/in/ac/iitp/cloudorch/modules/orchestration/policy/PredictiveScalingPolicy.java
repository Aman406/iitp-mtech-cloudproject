package in.ac.iitp.cloudorch.modules.orchestration.policy;

import in.ac.iitp.cloudorch.framework.execution.ControlObservation;
import in.ac.iitp.cloudorch.framework.execution.ScalingDecision;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;
import in.ac.iitp.cloudorch.framework.forecast.Forecaster;

/**
 * Predictive scaling driven by a workload forecaster.
 *
 * <p>At each control interval the policy feeds the observed arrival count to the forecaster and
 * sizes the pool for the forecast {@code lookaheadIntervals} ahead (normally chosen to cover the
 * provisioning delay):
 *
 * <pre>
 *   lambda   = forecastArrivals / intervalSeconds                (tasks/s)
 *   S        = observed mean service time (or the initial guess before any task completes)
 *   base     = lambda * S / targetUtilization * (1 + safetyMargin)
 *   backlog  = queueLength * S / (intervalSeconds * backlogDrainIntervals)
 *   required = ceil(base + backlog)
 * </pre>
 *
 * {@code lambda * S} is the offered load in busy workers (Little's law); dividing by the target
 * utilization leaves headroom for queueing; the backlog term adds capacity to clear tasks already
 * waiting. Scale-in is delayed by a cooldown to avoid oscillation. When {@code costAware} is set,
 * scale-in defers termination to the end of each worker's paid billing block.
 */
public final class PredictiveScalingPolicy implements ScalingPolicy {

    private final Forecaster forecaster;
    private final double targetUtilization;
    private final double safetyMargin;
    private final double backlogDrainIntervals;
    private final double initialMeanServiceSeconds;
    private final int lookaheadIntervals;
    private final int scaleInCooldownIntervals;
    private final boolean costAware;

    private int lastIncreaseInterval = Integer.MIN_VALUE / 2;

    public PredictiveScalingPolicy(Forecaster forecaster, double targetUtilization, double safetyMargin,
                                   double backlogDrainIntervals, double initialMeanServiceSeconds,
                                   int lookaheadIntervals, int scaleInCooldownIntervals, boolean costAware) {
        if (!(targetUtilization > 0 && targetUtilization <= 1)) {
            throw new IllegalArgumentException("targetUtilization must be in (0, 1]");
        }
        if (safetyMargin < 0 || !(backlogDrainIntervals > 0) || !(initialMeanServiceSeconds > 0)
                || lookaheadIntervals < 1 || scaleInCooldownIntervals < 0) {
            throw new IllegalArgumentException("invalid predictive policy parameters");
        }
        this.forecaster = forecaster;
        this.targetUtilization = targetUtilization;
        this.safetyMargin = safetyMargin;
        this.backlogDrainIntervals = backlogDrainIntervals;
        this.initialMeanServiceSeconds = initialMeanServiceSeconds;
        this.lookaheadIntervals = lookaheadIntervals;
        this.scaleInCooldownIntervals = scaleInCooldownIntervals;
        this.costAware = costAware;
    }

    @Override
    public String name() {
        return (costAware ? "PREDICTIVE-CA[" : "PREDICTIVE[") + forecaster.name() + "]";
    }

    @Override
    public boolean costAwareScaleIn() {
        return costAware;
    }

    @Override
    public ScalingDecision decide(ControlObservation obs) {
        forecaster.observe(obs.arrivalsLastInterval());
        double predicted = forecaster.forecast(lookaheadIntervals);
        if (Double.isNaN(predicted)) {
            predicted = obs.arrivalsLastInterval();
        }
        int required = requiredWorkers(predicted, obs);

        int current = obs.committedWorkers();
        int target = required;
        if (required >= current) {
            if (required > current) {
                lastIncreaseInterval = obs.intervalIndex();
            }
        } else if (obs.intervalIndex() - lastIncreaseInterval < scaleInCooldownIntervals) {
            target = current; // recently scaled out: hold capacity for a while
        }
        return new ScalingDecision(target, forecaster.forecast(1));
    }

    /** Pool size needed for {@code predictedArrivals} in the next interval plus the current backlog. */
    int requiredWorkers(double predictedArrivals, ControlObservation obs) {
        double s = Double.isNaN(obs.meanServiceTimeEstimate()) ? initialMeanServiceSeconds : obs.meanServiceTimeEstimate();
        double lambda = Math.max(0, predictedArrivals) / obs.intervalSeconds();
        double base = lambda * s / targetUtilization * (1 + safetyMargin);
        double backlog = obs.queueLength() * s / (obs.intervalSeconds() * backlogDrainIntervals);
        return (int) Math.ceil(base + backlog - 1e-9);
    }
}
