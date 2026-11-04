package in.ac.iitp.cloudorch.framework.forecast;

/**
 * Linearly weighted moving average: with k values in the window (oldest first), value i gets
 * weight i (1..k), so the newest observation counts most. Reacts faster than SMA to level changes.
 */
public final class WeightedMovingAverageForecaster extends WindowedForecaster {

    public WeightedMovingAverageForecaster(int window) {
        super(window);
    }

    @Override
    protected double estimate() {
        double num = 0;
        double den = 0;
        int w = 1;
        for (double v : history) {
            num += w * v;
            den += w;
            w++;
        }
        return num / den;
    }

    @Override
    public String name() {
        return "WMA(" + window + ")";
    }

    @Override
    public Forecaster freshCopy() {
        return new WeightedMovingAverageForecaster(window);
    }
}
