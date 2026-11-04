package in.ac.iitp.cloudorch.modules.orchestration.policy;

import in.ac.iitp.cloudorch.framework.config.ExperimentConfig;
import in.ac.iitp.cloudorch.framework.cost.CostModel;
import in.ac.iitp.cloudorch.framework.execution.ControlObservation;
import in.ac.iitp.cloudorch.framework.execution.PoolConfig;
import in.ac.iitp.cloudorch.framework.execution.ScalingDecision;
import in.ac.iitp.cloudorch.framework.execution.ScalingPolicy;
import in.ac.iitp.cloudorch.framework.execution.SimulationSettings;
import in.ac.iitp.cloudorch.framework.forecast.SimpleMovingAverageForecaster;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyTest {

    private static ControlObservation obs(int index, long arrivals, int queue, int committed, double util, double meanService) {
        return new ControlObservation(60.0 * (index + 1), index, 60.0, arrivals, arrivals, queue,
                committed, committed, 0, committed, util, meanService, 1, 50);
    }

    @Test
    void fixedPolicyAlwaysReturnsItsSize() {
        FixedAllocationPolicy p = new FixedAllocationPolicy(7);
        assertEquals(7, p.decide(obs(0, 1000, 500, 3, 1.0, 2)).targetWorkers());
        assertEquals(7, p.initialWorkers(2));
        assertEquals("FIXED-7", p.name());
        assertTrue(Double.isNaN(p.decide(obs(1, 0, 0, 7, 0, 2)).forecastNextIntervalArrivals()));
    }

    @Test
    void reactiveScalesOutOnHighUtilizationOrQueue() {
        ReactiveThresholdPolicy p = new ReactiveThresholdPolicy(0.8, 0.3, 2.0, 4, 1, 1, 3);
        assertEquals(14, p.decide(obs(0, 0, 0, 10, 0.85, 2)).targetWorkers());
        assertEquals(18, p.decide(obs(1, 0, 30, 14, 0.5, 2)).targetWorkers());   // 30 / 14 >= 2 queued per worker
        assertEquals(18, p.decide(obs(2, 0, 0, 18, 0.5, 2)).targetWorkers());    // in band: hold
    }

    @Test
    void reactiveScaleInWaitsForCooldown() {
        ReactiveThresholdPolicy p = new ReactiveThresholdPolicy(0.8, 0.3, 2.0, 4, 1, 1, 3);
        assertEquals(14, p.decide(obs(0, 0, 0, 10, 0.9, 2)).targetWorkers());
        assertEquals(14, p.decide(obs(1, 0, 0, 14, 0.1, 2)).targetWorkers());    // too soon after scale-out
        assertEquals(14, p.decide(obs(2, 0, 0, 14, 0.1, 2)).targetWorkers());
        assertEquals(13, p.decide(obs(3, 0, 0, 14, 0.1, 2)).targetWorkers());
        assertEquals(13, p.decide(obs(4, 0, 0, 13, 0.1, 2)).targetWorkers());    // scale-in cooldown
        assertEquals(13, p.decide(obs(4, 0, 3, 13, 0.1, 2)).targetWorkers());    // queue not empty
    }

    @Test
    void predictiveSizingFormula() {
        PredictiveScalingPolicy p = new PredictiveScalingPolicy(new SimpleMovingAverageForecaster(2),
                0.8, 0.0, 1.0, 2.0, 1, 2, false);
        // 600 arrivals / 60 s = 10 tasks/s; 10 * 2 s / 0.8 = 25 workers
        assertEquals(25, p.requiredWorkers(600, obs(0, 0, 0, 10, 0.5, Double.NaN)));
        // backlog of 30 tasks * 2 s / 60 s = 1 extra worker
        assertEquals(26, p.requiredWorkers(600, obs(0, 0, 30, 10, 0.5, Double.NaN)));
        // observed mean service time overrides the initial guess: 10 * 3 / 0.8 = 37.5 -> 38
        assertEquals(38, p.requiredWorkers(600, obs(0, 0, 0, 10, 0.5, 3.0)));
    }

    @Test
    void predictiveUsesForecastAndHoldsCapacityDuringCooldown() {
        PredictiveScalingPolicy p = new PredictiveScalingPolicy(new SimpleMovingAverageForecaster(2),
                0.8, 0.0, 1.0, 2.0, 1, 2, false);
        ScalingDecision d0 = p.decide(obs(0, 600, 0, 10, 0.9, 2.0));
        assertEquals(25, d0.targetWorkers());
        assertEquals(600.0, d0.forecastNextIntervalArrivals(), 1e-12);
        ScalingDecision d1 = p.decide(obs(1, 60, 0, 25, 0.3, 2.0)); // SMA = 330 -> 13.75 -> 14, but cooldown
        assertEquals(25, d1.targetWorkers());
        assertEquals(330.0, d1.forecastNextIntervalArrivals(), 1e-12);
        ScalingDecision d2 = p.decide(obs(2, 60, 0, 25, 0.1, 2.0)); // SMA = 60 -> 2.5 -> 3
        assertEquals(3, d2.targetWorkers());
    }

    @Test
    void factoryBuildsConfiguredPolicies() {
        ExperimentConfig c = ExperimentConfig.of(Map.of(
                "policy.fixed.workers", "9",
                "policy.predictive.forecaster", "WMA:4"));
        SimulationSettings s = new SimulationSettings(60, new PoolConfig(5, 1, 20, 120), new CostModel(0.1, 60, 60), 10, 600);
        assertEquals("FIXED-9", PolicyFactory.create("FIXED", c, s).name());
        assertEquals("FIXED-3", PolicyFactory.create("fixed:3", c, s).name());
        assertEquals("REACTIVE", PolicyFactory.create("REACTIVE", c, s).name());
        ScalingPolicy pred = PolicyFactory.create("PREDICTIVE", c, s);
        ScalingPolicy ca = PolicyFactory.create("PREDICTIVE_COST_AWARE", c, s);
        assertEquals("PREDICTIVE[WMA(4)]", pred.name());
        assertEquals("PREDICTIVE-CA[WMA(4)]", ca.name());
        assertFalse(pred.costAwareScaleIn());
        assertTrue(ca.costAwareScaleIn());
        assertThrows(IllegalArgumentException.class, () -> PolicyFactory.create("MAGIC", c, s));
    }

    @Test
    void invalidPolicyParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FixedAllocationPolicy(0));
        assertThrows(IllegalArgumentException.class, () -> new ReactiveThresholdPolicy(0.3, 0.8, 2, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PredictiveScalingPolicy(
                new SimpleMovingAverageForecaster(2), 1.5, 0, 1, 2, 1, 0, false));
    }
}
