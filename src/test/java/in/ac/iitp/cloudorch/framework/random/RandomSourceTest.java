package in.ac.iitp.cloudorch.framework.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSourceTest {

    @Test
    void sameSeedGivesSameSequence() {
        RandomSource a = new RandomSource(7);
        RandomSource b = new RandomSource(7);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextDouble(), b.nextDouble(), 0.0);
        }
    }

    @Test
    void forkedStreamsAreReproducibleAndDistinct() {
        double x1 = new RandomSource(7).fork("arrivals").nextDouble();
        double x2 = new RandomSource(7).fork("arrivals").nextDouble();
        double y = new RandomSource(7).fork("service").nextDouble();
        double z = new RandomSource(8).fork("arrivals").nextDouble();
        assertEquals(x1, x2, 0.0);
        assertNotEquals(x1, y);
        assertNotEquals(x1, z);
    }

    @Test
    void forkDoesNotDependOnParentConsumption() {
        RandomSource p1 = new RandomSource(11);
        RandomSource p2 = new RandomSource(11);
        p2.nextDouble();
        p2.nextDouble();
        assertEquals(p1.fork("s").nextDouble(), p2.fork("s").nextDouble(), 0.0);
    }

    @Test
    void exponentialHasRequestedMean() {
        RandomSource r = new RandomSource(1);
        int n = 200_000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += r.exponential(2.5);
        }
        assertEquals(2.5, sum / n, 2.5 * 0.02);
    }

    @Test
    void lognormalHasRequestedMeanAndCv() {
        RandomSource r = new RandomSource(2);
        int n = 200_000;
        double sum = 0;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double v = r.lognormal(2.0, 0.5);
            assertTrue(v > 0);
            sum += v;
            sumSq += v * v;
        }
        double mean = sum / n;
        double sd = Math.sqrt(sumSq / n - mean * mean);
        assertEquals(2.0, mean, 0.03);
        assertEquals(0.5, sd / mean, 0.02);
    }

    @Test
    void rejectsInvalidParameters() {
        RandomSource r = new RandomSource(3);
        assertThrows(IllegalArgumentException.class, () -> r.exponential(0));
        assertThrows(IllegalArgumentException.class, () -> r.lognormal(-1, 0.5));
    }
}
