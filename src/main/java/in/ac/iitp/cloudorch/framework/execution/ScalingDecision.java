package in.ac.iitp.cloudorch.framework.execution;

/**
 * Output of a scaling policy.
 *
 * @param targetWorkers                desired number of committed workers (clamped by the pool limits)
 * @param forecastNextIntervalArrivals the policy's one-step-ahead forecast of arrivals in the next
 *                                     interval, used to measure forecast error; NaN for policies
 *                                     that do not forecast
 */
public record ScalingDecision(int targetWorkers, double forecastNextIntervalArrivals) {

    public static ScalingDecision withoutForecast(int targetWorkers) {
        return new ScalingDecision(targetWorkers, Double.NaN);
    }
}
