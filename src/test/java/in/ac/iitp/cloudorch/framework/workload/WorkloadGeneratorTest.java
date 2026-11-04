package in.ac.iitp.cloudorch.framework.workload;

import in.ac.iitp.cloudorch.framework.random.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadGeneratorTest {

    private static final ServiceTimeDistribution SERVICE =
            new ServiceTimeDistribution(ServiceTimeDistribution.Type.EXPONENTIAL, 1.0, 1.0);

    @Test
    void sameSeedProducesIdenticalTrace() {
        WorkloadProfile p = new WorkloadProfile(3, 0.5, 600, 0, 0.1, 6, 2, 60);
        WorkloadTrace a = WorkloadGenerator.generate(p, SERVICE, 1800, new RandomSource(42));
        WorkloadTrace b = WorkloadGenerator.generate(p, SERVICE, 1800, new RandomSource(42));
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.arrivalTime(i), b.arrivalTime(i), 0.0);
            assertEquals(a.serviceTime(i), b.serviceTime(i), 0.0);
        }
    }

    @Test
    void differentSeedsProduceDifferentTraces() {
        WorkloadProfile p = WorkloadProfile.constant(5);
        WorkloadTrace a = WorkloadGenerator.generate(p, SERVICE, 600, new RandomSource(1));
        WorkloadTrace b = WorkloadGenerator.generate(p, SERVICE, 600, new RandomSource(2));
        assertTrue(a.size() != b.size() || a.arrivalTime(0) != b.arrivalTime(0));
    }

    @Test
    void constantRateMatchesExpectedCount() {
        // Poisson with mean 10 * 20000 = 200000; std ~ 447, so 1 % tolerance is > 4 sigma
        WorkloadTrace t = WorkloadGenerator.generate(WorkloadProfile.constant(10), SERVICE, 20_000, new RandomSource(5));
        assertEquals(200_000, t.size(), 2_000);
    }

    @Test
    void arrivalsAreSortedAndInsideHorizon() {
        WorkloadProfile p = new WorkloadProfile(4, 0.8, 900, 1.0, 0, 10, 3, 120);
        WorkloadTrace t = WorkloadGenerator.generate(p, SERVICE, 3600, new RandomSource(9));
        for (int i = 0; i < t.size(); i++) {
            assertTrue(t.arrivalTime(i) >= 0 && t.arrivalTime(i) < 3600);
            assertTrue(t.serviceTime(i) > 0);
            if (i > 0) {
                assertTrue(t.arrivalTime(i) >= t.arrivalTime(i - 1));
            }
        }
    }

    @Test
    void diurnalShapeIsVisibleInCounts() {
        // amplitude 0.9, period = duration, phase 0: first half has the peak, second half the trough
        WorkloadProfile p = new WorkloadProfile(5, 0.9, 7200, 0, 0, 0, 1, 0);
        long[] counts = WorkloadGenerator.generate(p, SERVICE, 7200, new RandomSource(3)).arrivalCounts(3600);
        assertTrue(counts[0] > 2 * counts[1], Arrays.toString(counts));
    }

    @Test
    void burstsIncreaseArrivals() {
        WorkloadProfile calm = WorkloadProfile.constant(2);
        WorkloadProfile bursty = new WorkloadProfile(2, 0, 3600, 0, 0, 30, 4, 300);
        int calmN = WorkloadGenerator.generate(calm, SERVICE, 7200, new RandomSource(4)).size();
        int burstyN = WorkloadGenerator.generate(bursty, SERVICE, 7200, new RandomSource(4)).size();
        assertTrue(burstyN > calmN * 1.5, calmN + " vs " + burstyN);
    }

    @Test
    void intervalCountsRoundTrip() {
        long[] counts = {5, 0, 12, 3, 7};
        WorkloadTrace t = WorkloadGenerator.fromIntervalCounts(counts, 60, SERVICE, new RandomSource(1));
        assertEquals(27, t.size());
        assertEquals(300.0, t.durationSeconds(), 1e-9);
        assertArrayEquals(counts, t.arrivalCounts(60));
    }

    @Test
    void traceLoaderReadsHeaderedAndPlainFiles() throws IOException {
        Path dir = Files.createTempDirectory("trace");
        Path withHeader = dir.resolve("a.csv");
        Files.write(withHeader, List.of("interval,arrivals", "0,10", "1,20.4", "# comment", "", "2,0"));
        assertArrayEquals(new long[] {10, 20, 0}, TraceLoader.loadCounts(withHeader, "arrivals"));
        Path plain = dir.resolve("b.csv");
        Files.write(plain, List.of("3", "4"));
        assertArrayEquals(new long[] {3, 4}, TraceLoader.loadCounts(plain, "arrivals"));
        Path bad = dir.resolve("c.csv");
        Files.write(bad, List.of("interval,count", "0,1"));
        assertThrows(IOException.class, () -> TraceLoader.loadCounts(bad, "arrivals"));
    }

    @Test
    void profileValidation() {
        assertThrows(IllegalArgumentException.class, () -> WorkloadProfile.constant(0));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, 1.5, 60, 0, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, 0.5, 60, 0, 0, 1, 0.5, 10));
    }
}
