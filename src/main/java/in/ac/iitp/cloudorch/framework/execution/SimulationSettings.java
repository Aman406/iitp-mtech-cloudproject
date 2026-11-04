package in.ac.iitp.cloudorch.framework.execution;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.cost.CostModel;

/**
 * Everything the execution engine needs apart from the workload and the policy.
 *
 * @param controlIntervalSeconds how often the scaling policy is consulted (monitoring period)
 * @param pool                   worker-pool limits and provisioning delay
 * @param costModel              billing rules
 * @param slaLatencySeconds      latency threshold used to report SLA violations
 * @param maxDrainSeconds        after the workload ends, how long the engine keeps running to
 *                               finish queued tasks before stopping and counting the rest as incomplete
 */
public record SimulationSettings(double controlIntervalSeconds, PoolConfig pool, CostModel costModel,
                                 double slaLatencySeconds, double maxDrainSeconds) {

    public SimulationSettings {
        if (!(controlIntervalSeconds > 0)) {
            throw new IllegalArgumentException("controlIntervalSeconds must be > 0");
        }
        if (!(slaLatencySeconds > 0)) {
            throw new IllegalArgumentException("slaLatencySeconds must be > 0");
        }
        if (maxDrainSeconds < 0) {
            throw new IllegalArgumentException("maxDrainSeconds must be >= 0");
        }
    }

    /** Reads {@code control.*}, {@code pool.*}, {@code cost.*} and {@code sla.*} keys. */
    public static SimulationSettings fromConfig(ExperimentConfig c) {
        PoolConfig pool = new PoolConfig(
                c.getInt("pool.initialWorkers", 8),
                c.getInt("pool.minWorkers", 1),
                c.getInt("pool.maxWorkers", 40),
                c.getDouble("pool.provisioningDelaySeconds", 60));
        CostModel cost = new CostModel(
                c.getDouble("cost.pricePerWorkerHour", 0.10),
                c.getDouble("cost.billingGranularitySeconds", 60),
                c.getDouble("cost.minimumBilledSeconds", 60));
        return new SimulationSettings(
                c.getDouble("control.intervalSeconds", 60),
                pool, cost,
                c.getDouble("sla.latencySeconds", 10),
                c.getDouble("simulation.maxDrainSeconds", 3600));
    }
}
