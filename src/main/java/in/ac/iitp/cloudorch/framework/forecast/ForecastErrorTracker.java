package in.ac.iitp.cloudorch.framework.forecast;

/**
 * Accumulates forecast errors and reports standard accuracy metrics.
 *
 * <ul>
 *   <li>MAE  = mean |p - a|</li>
 *   <li>RMSE = sqrt(mean (p - a)^2)</li>
 *   <li>MAPE = 100 * mean |p - a| / a, over points with a &gt; 0 only (undefined at a = 0)</li>
 *   <li>sMAPE = 100 * mean 2|p - a| / (|a| + |p|), skipping points where both are 0</li>
 *   <li>Bias = mean (p - a); positive means over-forecasting</li>
 * </ul>
 * Points whose prediction is NaN (no forecast available) are skipped and counted separately.
 */
public final class ForecastErrorTracker {

    private long n;
    private double sumAbs;
    private double sumSq;
    private double sumSigned;
    private long mapeN;
    private double mapeSum;
    private long smapeN;
    private double smapeSum;
    private long skipped;

    public void add(double actual, double predicted) {
        if (Double.isNaN(actual)) {
            throw new IllegalArgumentException("actual must not be NaN");
        }
        if (Double.isNaN(predicted)) {
            skipped++;
            return;
        }
        double e = predicted - actual;
        n++;
        sumAbs += Math.abs(e);
        sumSq += e * e;
        sumSigned += e;
        if (actual > 0) {
            mapeN++;
            mapeSum += Math.abs(e) / actual;
        }
        double denom = Math.abs(actual) + Math.abs(predicted);
        if (denom > 0) {
            smapeN++;
            smapeSum += 2 * Math.abs(e) / denom;
        }
    }

    public long count() {
        return n;
    }

    public long skipped() {
        return skipped;
    }

    public double mae() {
        return n == 0 ? Double.NaN : sumAbs / n;
    }

    public double rmse() {
        return n == 0 ? Double.NaN : Math.sqrt(sumSq / n);
    }

    public double mapePercent() {
        return mapeN == 0 ? Double.NaN : 100.0 * mapeSum / mapeN;
    }

    public double smapePercent() {
        return smapeN == 0 ? Double.NaN : 100.0 * smapeSum / smapeN;
    }

    public double bias() {
        return n == 0 ? Double.NaN : sumSigned / n;
    }
}
