package in.ac.iitp.cloudorch.framework.forecast;

import java.util.Locale;

/**
 * Exponentially weighted moving average (simple exponential smoothing):
 * {@code s = alpha * x + (1 - alpha) * s}, initialised with the first observation.
 */
public final class ExponentialMovingAverageForecaster implements Forecaster {

    private final double alpha;
    private double level = Double.NaN;
    private int observations;

    public ExponentialMovingAverageForecaster(double alpha) {
        if (!(alpha > 0 && alpha <= 1)) {
            throw new IllegalArgumentException("alpha must be in (0, 1]");
        }
        this.alpha = alpha;
    }

    @Override
    public void observe(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("observation must be finite");
        }
        level = observations == 0 ? value : alpha * value + (1 - alpha) * level;
        observations++;
    }

    @Override
    public double forecast(int horizon) {
        if (horizon < 1) {
            throw new IllegalArgumentException("horizon must be >= 1");
        }
        return level;
    }

    @Override
    public int observations() {
        return observations;
    }

    @Override
    public void reset() {
        level = Double.NaN;
        observations = 0;
    }

    @Override
    public String name() {
        return String.format(Locale.ROOT, "EMA(%.2f)", alpha);
    }

    @Override
    public Forecaster freshCopy() {
        return new ExponentialMovingAverageForecaster(alpha);
    }
}
