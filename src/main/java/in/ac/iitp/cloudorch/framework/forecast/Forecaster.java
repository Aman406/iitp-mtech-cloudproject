package in.ac.iitp.cloudorch.framework.forecast;

/**
 * Online univariate forecaster over a series of per-interval workload observations
 * (for example, the number of task arrivals in each control interval).
 *
 * <p>Usage: call {@link #observe(double)} once per interval with the actual value, then
 * {@link #forecast(int)} to predict the value {@code horizon} intervals after the last observation.
 */
public interface Forecaster {

    /** Short identifier such as {@code SMA(5)}. */
    String name();

    void observe(double value);

    /**
     * Predicted value {@code horizon >= 1} steps after the most recent observation, or
     * {@code NaN} if nothing has been observed yet.
     */
    double forecast(int horizon);

    /** Number of observations received since construction or the last {@link #reset()}. */
    int observations();

    void reset();

    /** A new, empty forecaster with the same parameters. */
    Forecaster freshCopy();
}
