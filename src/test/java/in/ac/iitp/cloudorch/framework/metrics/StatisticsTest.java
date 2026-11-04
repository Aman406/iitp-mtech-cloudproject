package in.ac.iitp.cloudorch.framework.metrics;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsTest {

    @Test
    void nearestRankPercentiles() {
        double[] xs = new double[100];
        for (int i = 0; i < 100; i++) {
            xs[i] = 100 - i; // unsorted 100..1
        }
        assertEquals(95.0, Statistics.percentile(xs, 95), 0.0);
        assertEquals(50.0, Statistics.percentile(xs, 50), 0.0);
        assertEquals(100.0, Statistics.percentile(xs, 100), 0.0);
        assertEquals(1.0, Statistics.percentile(xs, 0.5), 0.0);
        assertEquals(100.0, xs[0], 0.0); // input not modified
    }

    @Test
    void percentileSmallSamples() {
        assertEquals(7.0, Statistics.percentile(new double[] {7}, 95), 0.0);
        // n = 10: rank = ceil(9.5) = 10 -> largest value
        assertEquals(10.0, Statistics.percentile(new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 95), 0.0);
        assertTrue(Double.isNaN(Statistics.percentile(new double[0], 95)));
        assertThrows(IllegalArgumentException.class, () -> Statistics.percentile(new double[] {1}, 0));
    }

    @Test
    void meanAndSampleStd() {
        double[] xs = {2, 4, 4, 4, 5, 5, 7, 9};
        assertEquals(5.0, Statistics.mean(xs), 1e-12);
        assertEquals(Math.sqrt(32.0 / 7), Statistics.sampleStdDev(xs), 1e-12);
        assertTrue(Double.isNaN(Statistics.sampleStdDev(new double[] {1})));
    }

    @Test
    void aggregatorGroupsAndIgnoresNaN() {
        MetricAggregator agg = new MetricAggregator();
        agg.add("A", Map.of("x", 1.0));
        agg.add("A", Map.of("x", 3.0));
        agg.add("B", Map.of("x", Double.NaN));
        assertEquals(List.of("A", "B"), agg.groups());
        assertEquals(2.0, agg.mean("A", "x"), 1e-12);
        assertEquals(Math.sqrt(2), agg.std("A", "x"), 1e-12);
        assertTrue(Double.isNaN(agg.mean("B", "x")));
        assertEquals(2, agg.runs("A"));
        assertEquals("[x]", Arrays.toString(agg.metrics("A").toArray()));
    }
}
