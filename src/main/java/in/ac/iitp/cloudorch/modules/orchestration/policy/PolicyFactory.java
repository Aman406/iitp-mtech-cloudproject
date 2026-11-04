package in.ac.iitp.cloudorch.modules.orchestration.policy;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;
import in.ac.iitp.cloudorch.framework.execution.SimulationSettings;
import in.ac.iitp.cloudorch.framework.forecast.ForecasterFactory;

import java.util.Locale;

/**
 * Builds a fresh policy instance from a spec in {@code policies=...}:
 * {@code FIXED} (uses {@code policy.fixed.workers}), {@code FIXED:<n>}, {@code REACTIVE},
 * {@code PREDICTIVE} and {@code PREDICTIVE_COST_AWARE}.
 */
public final class PolicyFactory {

    private PolicyFactory() {
    }

    public static ScalingPolicy create(String spec, ExperimentConfig c, SimulationSettings settings) {
        String[] parts = spec.trim().split(":");
        String kind = parts[0].trim().toUpperCase(Locale.ROOT);
        switch (kind) {
            case "FIXED": {
                int n = parts.length > 1 ? Integer.parseInt(parts[1].trim())
                        : c.getInt("policy.fixed.workers", settings.pool().initialWorkers());
                return new FixedAllocationPolicy(n);
            }
            case "REACTIVE":
                return new ReactiveThresholdPolicy(
                        c.getDouble("policy.reactive.upperUtilization", 0.80),
                        c.getDouble("policy.reactive.lowerUtilization", 0.40),
                        c.getDouble("policy.reactive.queuePerWorkerThreshold", 2.0),
                        c.getInt("policy.reactive.scaleOutStep", 4),
                        c.getInt("policy.reactive.scaleInStep", 2),
                        c.getInt("policy.reactive.scaleOutCooldownIntervals", 1),
                        c.getInt("policy.reactive.scaleInCooldownIntervals", 3));
            case "PREDICTIVE":
            case "PREDICTIVE_COST_AWARE": {
                int defaultLookahead = Math.max(1, (int) Math.ceil(
                        settings.pool().provisioningDelaySeconds() / settings.controlIntervalSeconds()));
                return new PredictiveScalingPolicy(
                        ForecasterFactory.create(c.getString("policy.predictive.forecaster", "SMA:5")),
                        c.getDouble("policy.predictive.targetUtilization", 0.75),
                        c.getDouble("policy.predictive.safetyMargin", 0.10),
                        c.getDouble("policy.predictive.backlogDrainIntervals", 1.0),
                        c.getDouble("policy.predictive.initialMeanServiceSeconds",
                                c.getDouble("workload.service.meanSeconds", 2.0)),
                        c.getInt("policy.predictive.lookaheadIntervals", defaultLookahead),
                        c.getInt("policy.predictive.scaleInCooldownIntervals", 2),
                        kind.equals("PREDICTIVE_COST_AWARE"));
            }
            default:
                throw new IllegalArgumentException("unknown policy '" + spec
                        + "'; expected FIXED, FIXED:<n>, REACTIVE, PREDICTIVE or PREDICTIVE_COST_AWARE");
        }
    }
}
