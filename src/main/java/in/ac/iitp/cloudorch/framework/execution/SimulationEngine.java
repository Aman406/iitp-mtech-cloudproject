package in.ac.iitp.cloudorch.framework.execution;

import in.ac.iitp.cloudorch.framework.cost.CostModel;
import in.ac.iitp.cloudorch.framework.forecast.ForecastErrorTracker;
import in.ac.iitp.cloudorch.framework.workload.WorkloadTrace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Deterministic discrete-event simulator of a pool of identical workers serving one FIFO task
 * queue — the shared task execution framework used by the research modules.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li>Tasks arrive at the times in the {@link WorkloadTrace} and wait in a single FIFO queue.</li>
 *   <li>Each worker runs one task at a time, non-preemptively; a task's service time does not
 *       depend on which worker runs it.</li>
 *   <li>Every {@code controlIntervalSeconds} the {@link ScalingPolicy} observes monitoring data
 *       and returns a target pool size. Scale-out launches workers that become ready after
 *       {@code provisioningDelaySeconds}. Scale-in first cancels workers still provisioning, then
 *       removes idle workers, then marks busy workers to terminate after their current task
 *       (graceful draining). Cost-aware policies defer removal to the end of the paid billing block.</li>
 *   <li>Workers present at t = 0 are pre-warmed (ready immediately).</li>
 *   <li>The control loop stops at the first control tick at or after the end of the workload
 *       window. From then on no scaling happens: tasks still queued are drained by the workers
 *       present at that moment, and each worker is released as soon as it has no more work
 *       (idle workers immediately). The run ends when every task has completed, or after
 *       {@code maxDrainSeconds}, when remaining tasks are counted as incomplete.</li>
 * </ul>
 * Simulated time is independent of wall-clock time, so results are exactly reproducible.
 */
public final class SimulationEngine {

    private static final double EPS = 1e-9;

    private enum Type { ARRIVAL, COMPLETION, WORKER_READY, TERMINATE, CONTROL_TICK }

    private record Event(double time, long seq, Type type, int ref, long token) {
    }

    private enum State { PROVISIONING, IDLE, BUSY, TERMINATED }

    private static final class Worker {
        final int id;
        final double launchTime;
        double readyTime = Double.NaN;
        double terminateTime = Double.NaN;
        State state;
        int currentTask = -1;
        /** Busy worker that will terminate after finishing its current task. */
        boolean draining;
        /** Cost-aware removal time (end of paid billing block), NaN if not scheduled. */
        double pendingTerminationAt = Double.NaN;
        long terminationToken;
        long tasksCompleted;
        double busySeconds;

        Worker(int id, double launchTime) {
            this.id = id;
            this.launchTime = launchTime;
        }

        boolean markedForRemoval() {
            return draining || !Double.isNaN(pendingTerminationAt);
        }
    }

    private final WorkloadTrace trace;
    private final ScalingPolicy policy;
    private final SimulationSettings settings;
    private final CostModel cost;

    private final PriorityQueue<Event> events = new PriorityQueue<>(
            Comparator.comparingDouble(Event::time).thenComparingLong(Event::seq));
    private long seq;
    private final Deque<Integer> queue = new ArrayDeque<>();
    private final List<Worker> live = new ArrayList<>();
    private final List<Worker> allWorkers = new ArrayList<>();

    private final double[] startTimes;
    private final double[] endTimes;
    private int nextArrival;
    private int completed;
    private double serviceSumCompleted;
    private double lastCompletionTime;

    // time integrals
    private double lastClock;
    private double aliveAcc;
    private double readyAcc;
    private double busyAcc;
    private double queueAcc;

    // per-interval counters
    private long arrivalsThisInterval;
    private long completionsThisInterval;
    private double busyAtLastTick;
    private double readyAtLastTick;
    private int intervalIndex;
    private double pendingForecast = Double.NaN;
    private boolean workloadEnded;

    private final List<IntervalRecord> intervals = new ArrayList<>();
    private final ForecastErrorTracker forecastErrors = new ForecastErrorTracker();
    private int scaleOutActions;
    private int scaleInActions;
    private int peakAlive;

    private SimulationEngine(WorkloadTrace trace, ScalingPolicy policy, SimulationSettings settings) {
        this.trace = trace;
        this.policy = policy;
        this.settings = settings;
        this.cost = settings.costModel();
        this.startTimes = new double[trace.size()];
        this.endTimes = new double[trace.size()];
        Arrays.fill(startTimes, Double.NaN);
        Arrays.fill(endTimes, Double.NaN);
    }

    /** Runs one complete simulation. The policy instance should be fresh (policies are stateful). */
    public static SimulationResult simulate(WorkloadTrace trace, ScalingPolicy policy, SimulationSettings settings) {
        return new SimulationEngine(trace, policy, settings).run();
    }

    private SimulationResult run() {
        double duration = trace.durationSeconds();
        double hardStop = duration + settings.maxDrainSeconds();
        PoolConfig pool = settings.pool();

        int initialWorkers = pool.clamp(policy.initialWorkers(pool.initialWorkers()));
        for (int i = 0; i < initialWorkers; i++) {
            Worker w = newWorker(0.0);
            w.state = State.IDLE;
            w.readyTime = 0.0;
        }
        peakAlive = live.size();
        scheduleNextArrival();
        schedule(settings.controlIntervalSeconds(), Type.CONTROL_TICK, -1, 0);

        double now = 0;
        while (!events.isEmpty()) {
            Event e = events.peek();
            if (e.time() > hardStop + EPS) {
                break;
            }
            events.poll();
            now = e.time();
            advanceClock(now);
            switch (e.type()) {
                case ARRIVAL -> onArrival(e.ref(), now);
                case COMPLETION -> onCompletion(allWorkers.get(e.ref()), now);
                case WORKER_READY -> onReady(allWorkers.get(e.ref()), now);
                case TERMINATE -> onScheduledTermination(allWorkers.get(e.ref()), e.token(), now);
                case CONTROL_TICK -> onControlTick(now);
                default -> throw new IllegalStateException();
            }
            if (allTasksDone() && workloadEnded) {
                break;
            }
        }

        double endTime = allTasksDone()
                ? Math.max(Math.max(duration, lastCompletionTime), lastClock)
                : hardStop;
        advanceClock(endTime);
        for (Worker w : new ArrayList<>(live)) {
            terminate(w, endTime);
        }

        List<WorkerRecord> workerRecords = new ArrayList<>();
        for (Worker w : allWorkers) {
            workerRecords.add(new WorkerRecord(w.id, w.launchTime, w.readyTime, w.terminateTime, w.tasksCompleted, w.busySeconds));
        }
        double[] arrivals = new double[trace.size()];
        for (int i = 0; i < arrivals.length; i++) {
            arrivals[i] = trace.arrivalTime(i);
        }
        return new SimulationResult(policy.name(), settings, duration, endTime, arrivals,
                startTimes.clone(), endTimes.clone(), List.copyOf(intervals), List.copyOf(workerRecords),
                aliveAcc, readyAcc, busyAcc, queueAcc, scaleOutActions, scaleInActions,
                allWorkers.size() - initialWorkers, peakAlive, forecastErrors);
    }

    private boolean allTasksDone() {
        return nextArrival >= trace.size() && completed == trace.size();
    }

    // ------------------------------------------------------------------ events

    private void onArrival(int task, double now) {
        queue.addLast(task);
        arrivalsThisInterval++;
        nextArrival++;
        scheduleNextArrival();
        dispatch(now);
    }

    private void onCompletion(Worker w, double now) {
        int task = w.currentTask;
        endTimes[task] = now;
        double s = trace.serviceTime(task);
        w.busySeconds += s;
        w.tasksCompleted++;
        w.currentTask = -1;
        completed++;
        completionsThisInterval++;
        serviceSumCompleted += s;
        lastCompletionTime = now;
        boolean pendingDue = !Double.isNaN(w.pendingTerminationAt) && w.pendingTerminationAt <= now + EPS;
        if (w.draining || pendingDue || (workloadEnded && queue.isEmpty())) {
            terminate(w, now);
        } else {
            w.state = State.IDLE;
        }
        dispatch(now);
    }

    private void onReady(Worker w, double now) {
        if (w.state != State.PROVISIONING) {
            return; // cancelled while provisioning
        }
        w.state = State.IDLE;
        w.readyTime = now;
        if (workloadEnded && queue.isEmpty()) {
            terminate(w, now);
            return;
        }
        dispatch(now);
    }

    /** Stops the control loop and releases every worker that has nothing left to do. */
    private void endWorkload(double now) {
        workloadEnded = true;
        if (queue.isEmpty()) {
            for (Worker w : new ArrayList<>(live)) {
                if (w.state == State.IDLE || w.state == State.PROVISIONING) {
                    terminate(w, now);
                }
            }
        }
    }

    private void onScheduledTermination(Worker w, long token, double now) {
        if (w.state == State.TERMINATED || token != w.terminationToken || Double.isNaN(w.pendingTerminationAt)) {
            return; // reclaimed by a later scale-out, or already gone
        }
        if (w.state == State.BUSY) {
            w.draining = true;
        } else {
            terminate(w, now);
        }
    }

    private void onControlTick(double now) {
        double busyDelta = busyAcc - busyAtLastTick;
        double readyDelta = readyAcc - readyAtLastTick;
        double utilization = readyDelta > EPS ? busyDelta / readyDelta : 0.0;
        busyAtLastTick = busyAcc;
        readyAtLastTick = readyAcc;

        double forecastForThisInterval = pendingForecast;
        if (!Double.isNaN(forecastForThisInterval)) {
            forecastErrors.add(arrivalsThisInterval, forecastForThisInterval);
        }

        PoolConfig pool = settings.pool();
        ControlObservation obs = new ControlObservation(
                now, intervalIndex, settings.controlIntervalSeconds(),
                arrivalsThisInterval, completionsThisInterval, queue.size(),
                countCommitted(), countReady(), countState(State.PROVISIONING), countState(State.BUSY),
                utilization,
                completed == 0 ? Double.NaN : serviceSumCompleted / completed,
                pool.minWorkers(), pool.maxWorkers());

        ScalingDecision decision = policy.decide(obs);
        int target = pool.clamp(decision.targetWorkers());
        if (!queue.isEmpty() && target < 1) {
            target = 1; // safety: never strand queued tasks with zero workers
        }
        applyTarget(target, now);
        dispatch(now);

        intervals.add(new IntervalRecord(intervalIndex, now - settings.controlIntervalSeconds(), now,
                arrivalsThisInterval, forecastForThisInterval, completionsThisInterval,
                target, countCommitted(), countReady(), countState(State.PROVISIONING), queue.size(),
                utilization, aliveAcc));

        pendingForecast = decision.forecastNextIntervalArrivals();
        arrivalsThisInterval = 0;
        completionsThisInterval = 0;
        intervalIndex++;
        if (now >= trace.durationSeconds() - EPS) {
            endWorkload(now);
        } else {
            schedule(now + settings.controlIntervalSeconds(), Type.CONTROL_TICK, -1, 0);
        }
    }

    // ------------------------------------------------------------------ scaling

    private void applyTarget(int target, double now) {
        int current = countCommitted();
        if (target > current) {
            int need = target - current;
            // 1. reclaim workers marked for removal: they are already running (and paid for)
            for (Worker w : live) {
                if (need == 0) {
                    break;
                }
                if (w.markedForRemoval()) {
                    w.draining = false;
                    w.pendingTerminationAt = Double.NaN;
                    w.terminationToken++;
                    need--;
                }
            }
            // 2. launch new workers
            for (; need > 0; need--) {
                Worker w = newWorker(now);
                if (settings.pool().provisioningDelaySeconds() <= 0) {
                    w.state = State.IDLE;
                    w.readyTime = now;
                } else {
                    w.state = State.PROVISIONING;
                    schedule(now + settings.pool().provisioningDelaySeconds(), Type.WORKER_READY, w.id, 0);
                }
            }
            peakAlive = Math.max(peakAlive, live.size());
            scaleOutActions++;
        } else if (target < current) {
            int excess = current - target;
            List<Worker> candidates = new ArrayList<>();
            for (Worker w : live) {
                if (!w.markedForRemoval()) {
                    candidates.add(w);
                }
            }
            if (policy.costAwareScaleIn()) {
                candidates.sort(Comparator
                        .comparingDouble((Worker w) -> cost.nextBillingBoundary(w.launchTime, now))
                        .thenComparingInt(w -> removalRank(w.state))
                        .thenComparing(Comparator.comparingInt((Worker w) -> w.id).reversed()));
                for (int i = 0; i < excess; i++) {
                    Worker w = candidates.get(i);
                    double boundary = cost.nextBillingBoundary(w.launchTime, now);
                    if (boundary <= now + EPS) {
                        removeNow(w, now);
                    } else {
                        w.pendingTerminationAt = boundary;
                        w.terminationToken++;
                        schedule(boundary, Type.TERMINATE, w.id, w.terminationToken);
                    }
                }
            } else {
                candidates.sort(Comparator
                        .comparingInt((Worker w) -> removalRank(w.state))
                        .thenComparing(Comparator.comparingDouble((Worker w) -> w.launchTime).reversed())
                        .thenComparing(Comparator.comparingInt((Worker w) -> w.id).reversed()));
                for (int i = 0; i < excess; i++) {
                    removeNow(candidates.get(i), now);
                }
            }
            scaleInActions++;
        }
    }

    /** Removal preference: provisioning first, then idle, then busy. */
    private static int removalRank(State s) {
        return switch (s) {
            case PROVISIONING -> 0;
            case IDLE -> 1;
            case BUSY -> 2;
            case TERMINATED -> 3;
        };
    }

    private void removeNow(Worker w, double now) {
        if (w.state == State.BUSY) {
            w.draining = true;
        } else {
            terminate(w, now);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void dispatch(double now) {
        while (!queue.isEmpty()) {
            Worker w = pickIdleWorker();
            if (w == null) {
                return;
            }
            int task = queue.pollFirst();
            w.state = State.BUSY;
            w.currentTask = task;
            startTimes[task] = now;
            schedule(now + trace.serviceTime(task), Type.COMPLETION, w.id, 0);
        }
    }

    /** Lowest-id idle worker, preferring workers not scheduled for removal. */
    private Worker pickIdleWorker() {
        Worker fallback = null;
        for (Worker w : live) {
            if (w.state == State.IDLE) {
                if (!w.markedForRemoval()) {
                    return w;
                }
                if (fallback == null) {
                    fallback = w;
                }
            }
        }
        return fallback;
    }

    private Worker newWorker(double now) {
        Worker w = new Worker(allWorkers.size(), now);
        allWorkers.add(w);
        live.add(w);
        return w;
    }

    private void terminate(Worker w, double now) {
        w.state = State.TERMINATED;
        w.terminateTime = now;
        w.draining = false;
        w.pendingTerminationAt = Double.NaN;
        live.remove(w);
    }

    private void scheduleNextArrival() {
        if (nextArrival < trace.size()) {
            schedule(trace.arrivalTime(nextArrival), Type.ARRIVAL, nextArrival, 0);
        }
    }

    private void schedule(double time, Type type, int ref, long token) {
        events.add(new Event(time, seq++, type, ref, token));
    }

    private void advanceClock(double now) {
        double dt = now - lastClock;
        if (dt > 0) {
            int ready = 0;
            int busy = 0;
            for (Worker w : live) {
                if (w.state == State.IDLE) {
                    ready++;
                } else if (w.state == State.BUSY) {
                    ready++;
                    busy++;
                }
            }
            aliveAcc += dt * live.size();
            readyAcc += dt * ready;
            busyAcc += dt * busy;
            queueAcc += dt * queue.size();
            lastClock = now;
        }
    }

    private int countCommitted() {
        int n = 0;
        for (Worker w : live) {
            if (!w.markedForRemoval()) {
                n++;
            }
        }
        return n;
    }

    private int countReady() {
        int n = 0;
        for (Worker w : live) {
            if (w.state == State.IDLE || w.state == State.BUSY) {
                n++;
            }
        }
        return n;
    }

    private int countState(State s) {
        int n = 0;
        for (Worker w : live) {
            if (w.state == s) {
                n++;
            }
        }
        return n;
    }
}
