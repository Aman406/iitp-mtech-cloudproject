package in.ac.iitp.cloudorch.framework.forecast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastErrorTrackerTest {

    @Test
    void computesStandardMetricsByHand() {
        ForecastErrorTracker t = new ForecastErrorTracker();
        t.add(10, 12); // e = +2
        t.add(20, 15); // e = -5
        t.add(40, 40); // e = 0
        assertEquals(3, t.count());
        assertEquals(7.0 / 3, t.mae(), 1e-12);
        assertEquals(Math.sqrt(29.0 / 3), t.rmse(), 1e-12);
        assertEquals(100.0 * (0.2 + 0.25 + 0) / 3, t.mapePercent(), 1e-12);
        assertEquals(-1.0, t.bias(), 1e-12);
        double smape = 100.0 * (2 * 2 / 22.0 + 2 * 5 / 35.0 + 0) / 3;
        assertEquals(smape, t.smapePercent(), 1e-12);
    }

    @Test
    void mapeSkipsZeroActualsAndNaNPredictionsAreSkipped() {
        ForecastErrorTracker t = new ForecastErrorTracker();
        t.add(0, 5);
        t.add(10, 5);
        t.add(10, Double.NaN);
        assertEquals(2, t.count());
        assertEquals(1, t.skipped());
        assertEquals(50.0, t.mapePercent(), 1e-12); // only the a = 10 point
        assertEquals(5.0, t.mae(), 1e-12);
    }

    @Test
    void emptyTrackerReportsNaN() {
        ForecastErrorTracker t = new ForecastErrorTracker();
        assertTrue(Double.isNaN(t.mae()));
        assertTrue(Double.isNaN(t.rmse()));
        assertTrue(Double.isNaN(t.mapePercent()));
    }
}
