package in.ac.iitp.cloudorch.framework.cost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CostModelTest {

    @Test
    void perMinuteBillingRoundsUp() {
        CostModel m = new CostModel(0.12, 60, 60);
        assertEquals(60, m.billedSeconds(1), 1e-9);
        assertEquals(60, m.billedSeconds(60), 1e-9);
        assertEquals(120, m.billedSeconds(60.5), 1e-9);
        assertEquals(60, m.billedSeconds(0), 1e-9);          // launch-and-cancel still pays the minimum
        assertEquals(0.12 * 2, m.cost(7200), 1e-12);
    }

    @Test
    void perSecondBillingWithMinimum() {
        CostModel m = new CostModel(3.6, 1, 60);
        assertEquals(60, m.billedSeconds(10), 1e-9);
        assertEquals(61, m.billedSeconds(60.2), 1e-9);
        assertEquals(0.061, m.cost(60.2), 1e-12);
    }

    @Test
    void hourlyBillingBoundary() {
        CostModel m = new CostModel(1.0, 3600, 3600);
        assertEquals(3600, m.nextBillingBoundary(0, 60), 1e-9);
        assertEquals(3600, m.nextBillingBoundary(0, 3600), 1e-9);   // exactly at the boundary: now
        assertEquals(7300, m.nextBillingBoundary(100, 3800), 1e-9);
        assertEquals(2.0, m.cost(3601), 1e-12);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new CostModel(-1, 60, 60));
        assertThrows(IllegalArgumentException.class, () -> new CostModel(1, 0, 60));
    }
}
