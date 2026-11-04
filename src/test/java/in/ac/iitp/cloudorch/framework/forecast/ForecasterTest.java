package in.ac.iitp.cloudorch.framework.forecast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecasterTest {

    private static void feed(Forecaster f, double... values) {
        for (double v : values) {
            f.observe(v);
        }
    }

    @Test
    void emptyForecasterReturnsNaN() {
        assertTrue(Double.isNaN(new SimpleMovingAverageForecaster(3).forecast(1)));
        assertTrue(Double.isNaN(new ExponentialMovingAverageForecaster(0.5).forecast(1)));
    }

    @Test
    void naiveReturnsLastValue() {
        Forecaster f = new NaiveForecaster();
        feed(f, 3, 9, 4);
        assertEquals(4.0, f.forecast(1), 1e-12);
        assertEquals(4.0, f.forecast(5), 1e-12);
    }

    @Test
    void simpleMovingAverageUsesOnlyTheWindow() {
        Forecaster f = new SimpleMovingAverageForecaster(3);
        feed(f, 10);
        assertEquals(10.0, f.forecast(1), 1e-12);          // partial window
        feed(f, 20);
        assertEquals(15.0, f.forecast(1), 1e-12);
        feed(f, 30, 40);
        assertEquals(30.0, f.forecast(1), 1e-12);          // (20 + 30 + 40) / 3
        assertEquals(30.0, f.forecast(4), 1e-12);          // flat for any horizon
        assertEquals(4, f.observations());
    }

    @Test
    void weightedMovingAverageWeighsRecentValuesMore() {
        Forecaster f = new WeightedMovingAverageForecaster(3);
        feed(f, 100, 10, 20, 30);
        // window = [10, 20, 30], weights 1, 2, 3 -> (10 + 40 + 90) / 6
        assertEquals(140.0 / 6.0, f.forecast(1), 1e-12);
    }

    @Test
    void exponentialMovingAverageRecursion() {
        Forecaster f = new ExponentialMovingAverageForecaster(0.5);
        feed(f, 10);
        assertEquals(10.0, f.forecast(1), 1e-12);
        feed(f, 20);
        assertEquals(15.0, f.forecast(1), 1e-12);
        feed(f, 40);
        assertEquals(27.5, f.forecast(1), 1e-12);
    }

    @Test
    void resetAndFreshCopyStartEmpty() {
        Forecaster f = new SimpleMovingAverageForecaster(2);
        feed(f, 1, 2);
        Forecaster copy = f.freshCopy();
        assertTrue(Double.isNaN(copy.forecast(1)));
        assertEquals("SMA(2)", copy.name());
        f.reset();
        assertEquals(0, f.observations());
        assertTrue(Double.isNaN(f.forecast(1)));
    }

    @Test
    void factoryParsesSpecs() {
        assertEquals("NAIVE", ForecasterFactory.create("naive").name());
        assertEquals("SMA(5)", ForecasterFactory.create("SMA:5").name());
        assertEquals("WMA(6)", ForecasterFactory.create(" wma : 6 ").name());
        assertEquals("EMA(0.30)", ForecasterFactory.create("EMA:0.3").name());
        assertEquals(5, ForecasterFactory.windowOf(ForecasterFactory.create("SMA:5")));
        assertEquals(1, ForecasterFactory.windowOf(ForecasterFactory.create("EMA:0.3")));
    }

    @Test
    void factoryRejectsBadSpecs() {
        assertThrows(IllegalArgumentException.class, () -> ForecasterFactory.create("ARIMA:1"));
        assertThrows(IllegalArgumentException.class, () -> ForecasterFactory.create("SMA"));
        assertThrows(IllegalArgumentException.class, () -> ForecasterFactory.create("SMA:x"));
        assertThrows(IllegalArgumentException.class, () -> ForecasterFactory.create("SMA:0"));
        assertThrows(IllegalArgumentException.class, () -> ForecasterFactory.create("EMA:1.5"));
        assertThrows(IllegalArgumentException.class, () -> new SimpleMovingAverageForecaster(3).forecast(0));
    }
}
