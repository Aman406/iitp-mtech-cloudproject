package in.ac.iitp.cloudorch.framework.forecast;

/**
 * Simple moving average: the mean of the last {@code window} observations. Before the window is
 * full, the mean of all observations so far is used.
 */
public final class SimpleMovingAverageForecaster extends WindowedForecaster {

    public SimpleMovingAverageForecaster(int window) {
        super(window);
    }

    @Override
    protected double estimate() {
        double sum = 0;
        for (double v : history) {
            sum += v;
        }
        return sum / history.size();
    }

    @Override
    public String name() {
        return "SMA(" + window + ")";
    }

    @Override
    public Forecaster freshCopy() {
        return new SimpleMovingAverageForecaster(window);
    }
}
