package in.ac.iitp.cloudorch.framework.forecast;

import java.util.ArrayDeque;
import java.util.Deque;

/** Base class for forecasters that keep the last {@code window} observations. */
abstract class WindowedForecaster implements Forecaster {

    protected final int window;
    protected final Deque<Double> history = new ArrayDeque<>();
    private int observations;

    protected WindowedForecaster(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be >= 1");
        }
        this.window = window;
    }

    @Override
    public final void observe(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("observation must be finite");
        }
        history.addLast(value);
        if (history.size() > window) {
            history.removeFirst();
        }
        observations++;
    }

    @Override
    public final double forecast(int horizon) {
        if (horizon < 1) {
            throw new IllegalArgumentException("horizon must be >= 1");
        }
        return history.isEmpty() ? Double.NaN : estimate();
    }

    /** Estimate from the (non-empty) history; moving averages give the same value for every horizon. */
    protected abstract double estimate();

    @Override
    public final int observations() {
        return observations;
    }

    @Override
    public final void reset() {
        history.clear();
        observations = 0;
    }

    public int window() {
        return window;
    }
}
