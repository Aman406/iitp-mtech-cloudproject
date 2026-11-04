package in.ac.iitp.cloudorch.framework.execution;

/**
 * Worker-pool limits.
 *
 * @param initialWorkers           workers running at t = 0 (assumed pre-warmed, i.e. already ready)
 * @param minWorkers               lower bound applied to every scaling decision
 * @param maxWorkers               upper bound applied to every scaling decision (capacity/quota)
 * @param provisioningDelaySeconds time between launching a worker and it accepting tasks (cold start)
 */
public record PoolConfig(int initialWorkers, int minWorkers, int maxWorkers, double provisioningDelaySeconds) {

    public PoolConfig {
        if (minWorkers < 0 || maxWorkers < 1 || minWorkers > maxWorkers) {
            throw new IllegalArgumentException("require 0 <= minWorkers <= maxWorkers and maxWorkers >= 1");
        }
        if (initialWorkers < minWorkers || initialWorkers > maxWorkers) {
            throw new IllegalArgumentException("initialWorkers must be within [minWorkers, maxWorkers]");
        }
        if (provisioningDelaySeconds < 0) {
            throw new IllegalArgumentException("provisioningDelaySeconds must be >= 0");
        }
    }

    public int clamp(int target) {
        return Math.max(minWorkers, Math.min(maxWorkers, target));
    }
}
