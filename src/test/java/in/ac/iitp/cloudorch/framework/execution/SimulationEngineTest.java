package in.ac.iitp.cloudorch.framework.execution;

import in.ac.iitp.cloudorch.framework.cost.CostModel;
import in.ac.iitp.cloudorch.framework.metrics.SummaryMetrics;
import in.ac.iitp.cloudorch.framework.random.RandomSource;
import in.ac.iitp.cloudorch.framework.workload.ServiceTimeDistribution;
import in.ac.iitp.cloudorch.framework.workload.WorkloadGenerator;
import in.ac.iitp.cloudorch.framework.workload.WorkloadProfile;
import in.ac.iitp.cloudorch.framework.workload.WorkloadTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    /** Returns targets[i] at the i-th control tick (last value repeated). Never forecasts. */
    private static final class ScriptedPolicy implements ScalingPolicy {
        private final int[] targets;
        private final boolean costAware;
        private int calls;

        ScriptedPolicy(boolean costAware, int... targets) {
            this.targets = targets;
            this.costAware = costAware;
        }

        @Override
        public String name() {
            return "SCRIPTED";
        }

        @Override
        public ScalingDecision decide(ControlObservation obs) {
            return ScalingDecision.withoutForecast(targets[Math.min(calls++, targets.length - 1)]);
        }

        @Override
        public boolean costAwareScaleIn() {
            return costAware;
        }
    }

    private static SimulationSettings settings(double interval, PoolConfig pool, CostModel cost) {
        return new SimulationSettings(interval, pool, cost, 10, 3600);
    }

    @Test
    void singleWorkerFifoQueueByHand() {
        WorkloadTrace trace = new WorkloadTrace(new double[] {0, 0, 0}, new double[] {1, 1, 1}, 100, "hand-made");
        SimulationSettings s = settings(10, new PoolConfig(1, 1, 4, 0), new CostModel(3.6, 1, 0));
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 1), s);

        assertEquals(0.0, r.startTimes()[0], 1e-12);
        assertEquals(1.0, r.startTimes()[1], 1e-12);
        assertEquals(2.0, r.startTimes()[2], 1e-12);
        assertEquals(3.0, r.endTimes()[2], 1e-12);

        SummaryMetrics m = SummaryMetrics.of(r);
        assertEquals(3, m.tasksCompleted());
        assertEquals(1.0, m.meanWaitSeconds(), 1e-12);        // (0 + 1 + 2) / 3
        assertEquals(2.0, m.p95WaitSeconds(), 1e-12);
        assertEquals(3.0, m.p95LatencySeconds(), 1e-12);
        assertEquals(100.0, m.makespanSeconds(), 1e-12);
        assertEquals(0.03, m.throughputTasksPerSecond(), 1e-12);
        assertEquals(100.0 / 3600, m.workerHours(), 1e-12);
        assertEquals(0.1, m.cost(), 1e-12);
        assertEquals(0.03, m.meanUtilization(), 1e-12);
        assertEquals(0.0, m.slaViolationPercent(), 1e-12);
    }

    @Test
    void twoWorkersServeInParallel() {
        WorkloadTrace trace = new WorkloadTrace(new double[] {0, 0}, new double[] {5, 5}, 60, "parallel");
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 2),
                settings(10, new PoolConfig(2, 1, 4, 0), new CostModel(1, 1, 0)));
        assertEquals(0.0, r.startTimes()[1], 1e-12);
        assertEquals(5.0, r.endTimes()[1], 1e-12);
    }

    @Test
    void scaleOutRespectsProvisioningDelayAndScaleInRemovesNewestIdleWorkers() {
        WorkloadTrace empty = new WorkloadTrace(new double[0], new double[0], 40, "empty");
        SimulationResult r = SimulationEngine.simulate(empty, new ScriptedPolicy(false, 3, 1),
                settings(10, new PoolConfig(1, 1, 4, 5), new CostModel(1, 1, 0)));

        assertEquals(3, r.workers().size());
        assertEquals(2, r.workersLaunched());
        assertEquals(3, r.peakAliveWorkers());
        WorkerRecord w1 = r.workers().get(1);
        assertEquals(10.0, w1.launchTime(), 1e-12);
        assertEquals(15.0, w1.readyTime(), 1e-12);
        assertEquals(20.0, w1.terminateTime(), 1e-12);
        assertEquals(40.0, r.workers().get(0).terminateTime(), 1e-12);
        assertEquals(40.0 + 10 + 10, r.workerAliveSeconds(), 1e-9);   // provisioning time is paid
        assertEquals(40.0 + 5 + 5, r.workerReadySeconds(), 1e-9);
    }

    @Test
    void cancelledProvisioningWorkerNeverBecomesReady() {
        WorkloadTrace empty = new WorkloadTrace(new double[0], new double[0], 30, "empty");
        SimulationResult r = SimulationEngine.simulate(empty, new ScriptedPolicy(false, 2, 1),
                settings(10, new PoolConfig(1, 1, 4, 30), new CostModel(1, 1, 0)));
        WorkerRecord w1 = r.workers().get(1);
        assertTrue(Double.isNaN(w1.readyTime()));
        assertEquals(20.0, w1.terminateTime(), 1e-12);
    }

    @Test
    void costAwareScaleInKeepsPaidWorkerUntilBillingBoundary() {
        WorkloadTrace empty = new WorkloadTrace(new double[0], new double[0], 7200, "empty");
        CostModel hourly = new CostModel(1.0, 3600, 3600);
        PoolConfig pool = new PoolConfig(2, 1, 4, 0);

        SimulationResult eager = SimulationEngine.simulate(empty, new ScriptedPolicy(false, 1), settings(60, pool, hourly));
        SimulationResult aware = SimulationEngine.simulate(empty, new ScriptedPolicy(true, 1), settings(60, pool, hourly));

        assertEquals(60.0, eager.workers().get(1).terminateTime(), 1e-9);
        assertEquals(3600.0, aware.workers().get(1).terminateTime(), 1e-9);
        // both pay one hour for the removed worker plus two for the remaining one
        assertEquals(SummaryMetrics.of(eager).cost(), SummaryMetrics.of(aware).cost(), 1e-12);
        assertEquals(3.0, SummaryMetrics.of(aware).cost(), 1e-12);
    }

    @Test
    void costAwareScaleOutReclaimsWorkerInsteadOfLaunching() {
        WorkloadTrace empty = new WorkloadTrace(new double[0], new double[0], 600, "empty");
        CostModel hourly = new CostModel(1.0, 3600, 3600);
        PoolConfig pool = new PoolConfig(2, 1, 4, 0);
        SimulationResult aware = SimulationEngine.simulate(empty, new ScriptedPolicy(true, 1, 2), settings(60, pool, hourly));
        SimulationResult eager = SimulationEngine.simulate(empty, new ScriptedPolicy(false, 1, 2), settings(60, pool, hourly));
        assertEquals(0, aware.workersLaunched());
        assertEquals(1, eager.workersLaunched());
    }

    @Test
    void conservationLawsHoldOnRandomWorkload() {
        WorkloadProfile p = new WorkloadProfile(3, 0.7, 1200, 0, 0, 6, 3, 120);
        WorkloadTrace trace = WorkloadGenerator.generate(p,
                new ServiceTimeDistribution(ServiceTimeDistribution.Type.LOGNORMAL, 2.0, 0.8), 3600, new RandomSource(99));
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 4, 9, 2, 12, 6, 1, 8),
                settings(60, new PoolConfig(4, 1, 20, 45), new CostModel(0.1, 60, 60)));

        double sumWait = 0;
        double sumService = 0;
        for (int i = 0; i < trace.size(); i++) {
            assertTrue(r.startTimes()[i] >= trace.arrivalTime(i) - 1e-9);
            assertEquals(r.startTimes()[i] + trace.serviceTime(i), r.endTimes()[i], 1e-6);
            sumWait += r.startTimes()[i] - trace.arrivalTime(i);
            sumService += trace.serviceTime(i);
        }
        assertEquals(trace.size(), SummaryMetrics.of(r).tasksCompleted());
        // integral of queue length over time equals total waiting time (exact for FIFO)
        assertEquals(sumWait, r.queueLengthIntegral(), 1e-6 * Math.max(1, sumWait));
        // integral of busy workers equals total service demand
        assertEquals(sumService, r.workerBusySeconds(), 1e-6 * sumService);
        double busyFromWorkers = r.workers().stream().mapToDouble(WorkerRecord::busySeconds).sum();
        assertEquals(sumService, busyFromWorkers, 1e-6 * sumService);
        assertTrue(r.workerBusySeconds() <= r.workerReadySeconds() + 1e-9);
        assertTrue(r.workerReadySeconds() <= r.workerAliveSeconds() + 1e-9);
    }

    @Test
    void identicalInputsGiveIdenticalResults() {
        WorkloadProfile p = new WorkloadProfile(4, 0.5, 900, 0, 0, 4, 2, 120);
        ServiceTimeDistribution svc = new ServiceTimeDistribution(ServiceTimeDistribution.Type.EXPONENTIAL, 1.5, 1);
        SimulationSettings s = settings(30, new PoolConfig(3, 1, 30, 30), new CostModel(0.1, 1, 60));
        SummaryMetrics a = SummaryMetrics.of(SimulationEngine.simulate(
                WorkloadGenerator.generate(p, svc, 1800, new RandomSource(5)), new ScriptedPolicy(false, 3, 8, 5), s));
        SummaryMetrics b = SummaryMetrics.of(SimulationEngine.simulate(
                WorkloadGenerator.generate(p, svc, 1800, new RandomSource(5)), new ScriptedPolicy(false, 3, 8, 5), s));
        assertEquals(a.toMap().toString(), b.toMap().toString());
    }

    @Test
    void forecastErrorIsMeasuredAgainstTheFollowingInterval() {
        // 2 arrivals in [0,10), 4 in [10,20); the policy always forecasts 3 for the next interval
        WorkloadTrace trace = new WorkloadTrace(new double[] {1, 2, 11, 12, 13, 14}, new double[] {0.1, 0.1, 0.1, 0.1, 0.1, 0.1},
                20, "forecast check");
        ScalingPolicy constantForecast = new ScalingPolicy() {
            @Override
            public String name() {
                return "CONST";
            }

            @Override
            public ScalingDecision decide(ControlObservation obs) {
                return new ScalingDecision(1, 3);
            }
        };
        SimulationResult r = SimulationEngine.simulate(trace, constantForecast,
                settings(10, new PoolConfig(1, 1, 2, 0), new CostModel(1, 1, 0)));
        assertTrue(Double.isNaN(r.intervals().get(0).forecastArrivals()));
        assertEquals(3.0, r.intervals().get(1).forecastArrivals(), 0.0);
        assertEquals(1, r.forecastErrors().count());
        assertEquals(1.0, r.forecastErrors().mae(), 1e-12); // |3 - 4|
    }

    @Test
    void workersAreReleasedWhenTheWorkloadWindowEnds() {
        // one task runs past the end of the 100 s window; the idle worker is released at t = 100
        WorkloadTrace trace = new WorkloadTrace(new double[] {95}, new double[] {20}, 100, "end of window");
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 2),
                settings(10, new PoolConfig(2, 1, 4, 0), new CostModel(1, 1, 0)));
        assertEquals(115.0, r.workers().get(0).terminateTime(), 1e-9);
        assertEquals(100.0, r.workers().get(1).terminateTime(), 1e-9);
        assertEquals(215.0, r.workerAliveSeconds(), 1e-9);
        assertEquals(115.0, r.endTime(), 1e-9);
        assertEquals(10, r.intervals().size()); // no control ticks after the window
    }

    @Test
    void backlogIsDrainedWithoutFurtherScalingAfterTheWindow() {
        double[] arrivals = new double[10];
        double[] services = new double[10];
        for (int i = 0; i < 10; i++) {
            arrivals[i] = 9.0;
            services[i] = 5.0;
        }
        WorkloadTrace trace = new WorkloadTrace(arrivals, services, 10, "backlog");
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 1, 4),
                settings(10, new PoolConfig(1, 1, 4, 0), new CostModel(1, 1, 0)));
        assertEquals(10, SummaryMetrics.of(r).tasksCompleted());
        assertEquals(1, r.workers().size());           // the 4-worker target is never applied after t = 10
        assertEquals(59.0, r.endTime(), 1e-9);          // 10 tasks * 5 s sequentially from t = 9
    }

    @Test
    void queuedTasksAreNeverStrandedWithZeroWorkers() {
        WorkloadTrace trace = new WorkloadTrace(new double[] {5, 15}, new double[] {30, 1}, 20, "zero target");
        SimulationResult r = SimulationEngine.simulate(trace, new ScriptedPolicy(false, 0),
                settings(10, new PoolConfig(1, 0, 4, 0), new CostModel(1, 1, 0)));
        assertEquals(2, SummaryMetrics.of(r).tasksCompleted());
    }
}
