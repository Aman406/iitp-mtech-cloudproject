package in.ac.iitp.cloudorch.framework.forecast;

/** Baseline: the next value equals the last observed value (a moving average of window 1). */
public final class NaiveForecaster extends WindowedForecaster {

    public NaiveForecaster() {
        super(1);
    }

    @Override
    protected double estimate() {
        return history.peekLast();
    }

    @Override
    public String name() {
        return "NAIVE";
    }

    @Override
    public Forecaster freshCopy() {
        return new NaiveForecaster();
    }
}
