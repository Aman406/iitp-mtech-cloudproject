# Cost-Aware Predictive Resource Orchestration for Distributed Cloud Workloads Using Workload Forecasting

*M.Tech (Cloud Computing), 3rd semester project prototype, IIT Patna. Research prototype, not production software.*

This is a single Java 17 / Maven application. It contains one **shared task execution framework** (a deterministic discrete-event simulator of a cloud worker pool) and two **independently executable research modules** built on it:

| Module | Question it answers | Entry point |
|---|---|---|
| **A — Workload Forecasting** | How accurately do moving-average forecasters predict per-interval task arrivals, and how does accuracy change with window size and horizon? | `in.ac.iitp.cloudorch.modules.forecasting.ForecastingMain` |
| **B — Cost-Aware Orchestration** | How do fixed, reactive and forecast-driven (predictive) worker scaling compare on queue wait, p95 latency, throughput, worker-hours, simulated cost and forecast error? | `in.ac.iitp.cloudorch.modules.orchestration.OrchestrationMain` |

Everything runs locally. There are no cloud accounts, no paid services and no network calls. The jar has **no runtime dependencies** beyond the JDK.

---

## 1. Verification status

| Check | Status |
|---|---|
| Main sources compile with `javac --release 17 -Xlint:all -Werror` (JDK 21.0.12) | ✅ done |
| Test sources compile with `-Xlint:all -Werror` | ✅ done |
| 66 unit/integration tests executed, 0 failures | ✅ done, **with a local JUnit API stand-in** (see the note below) |
| Executable jar built (with the `jar` tool) and both modules run with every sample config | ✅ done; outputs are in `sample-output/` |
| Re-running an experiment produces byte-identical CSV files | ✅ checked for `orchestration-default` |
| `mvn clean verify` | ❌ **not run.** The development sandbox's network policy blocks Maven Central, so Maven could not download its plugins or JUnit |

**Note on the tests.** The test classes use the standard JUnit 5 API: `@Test`, `Assertions.*`, `assertThrows`. Because JUnit could not be downloaded, the same test sources were compiled and run against a minimal re-implementation of the JUnit annotations and assertions, plus a reflective runner. That stand-in is not part of this repository. It was itself checked to report failures correctly. The real `mvn clean verify` should be the first thing you run on a machine with internet access (§3). If anything fails there, treat that result as authoritative.

The experiment results in this README are **simulation outputs on synthetic workloads**. They have not been validated against a real cloud deployment (see §11).

---

## 2. Project structure

```
cloud-orchestration-sim/
├── pom.xml                         Maven build (Java 17, JUnit 5.10.2, pinned plugin versions)
├── README.md
├── configs/                        sample experiment configurations (.properties)
│   ├── forecast-default.properties
│   ├── orchestration-default.properties
│   ├── orchestration-hourly-billing.properties
│   └── orchestration-slow-provisioning.properties
├── scripts/run-all.sh | run-all.cmd   build + run every sample experiment
├── sample-output/                  real console transcripts and CSVs from the runs described in §8
└── src/
    ├── main/java/in/ac/iitp/cloudorch/
    │   ├── App.java                         launcher: `forecast` | `orchestrate`
    │   ├── framework/                       ── SHARED TASK EXECUTION FRAMEWORK ──
    │   │   ├── config/      ExperimentConfig (properties + key=value overrides), CliSupport
    │   │   ├── random/      RandomSource (seeded, forkable streams)
    │   │   ├── workload/    WorkloadProfile, WorkloadGenerator, WorkloadTrace, ServiceTimeDistribution,
    │   │   │                TraceLoader (CSV replay), WorkloadFactory
    │   │   ├── forecast/    Forecaster API, NAIVE, SMA, WMA, EMA, ForecasterFactory, ForecastErrorTracker
    │   │   ├── execution/   SimulationEngine (discrete-event), ScalingPolicy API, PoolConfig,
    │   │   │                ControlObservation, ScalingDecision, IntervalRecord, WorkerRecord, ...
    │   │   ├── cost/        CostModel (price, billing granularity, minimum billed time)
    │   │   ├── metrics/     SummaryMetrics, Statistics (nearest-rank percentiles), MetricAggregator
    │   │   └── io/          CsvWriter, ConsoleTable
    │   └── modules/
    │       ├── forecasting/     ── MODULE A ── ForecastingExperiment, ForecastingMain
    │       └── orchestration/   ── MODULE B ── OrchestrationExperiment, OrchestrationMain
    │           └── policy/      FixedAllocationPolicy, ReactiveThresholdPolicy,
    │                            PredictiveScalingPolicy, PolicyFactory
    └── test/java/...            66 tests (framework, policies, both modules, architecture)
```

**Independence rule:** `framework` never imports `modules`, and the two modules never import each other. `ModuleIndependenceTest` enforces this on every build. Only the `App` launcher knows about both modules.

**Why not Spring Boot?** The application is an offline, single-process batch simulator. Spring Boot would add startup time, a large dependency tree and auto-configuration without providing any feature that is used here. It can be added later if a REST/dashboard front-end is required (§12).

---

## 3. Setup, build and test

**Prerequisites:** JDK 17 or newer (developed on JDK 21), and Maven 3.8+ with internet access the first time.

```bash
java -version          # must report 17 or later
mvn -version

cd cloud-orchestration-sim
mvn clean verify       # compiles, runs all JUnit tests, builds the jar
```

Expected artifact: `target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar`.

Other useful Maven commands:

```bash
mvn test                                    # tests only
mvn -Dtest=SimulationEngineTest test        # a single test class
mvn -DskipTests package                     # build the jar without tests (not recommended)
```

---

## 4. Running the two modules (exact commands)

All commands are run from the project root, after `mvn clean verify`.

### Module A — Workload Forecasting

```bash
# via the launcher
java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar forecast configs/forecast-default.properties

# or stand-alone, without the launcher
java -cp target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar \
     in.ac.iitp.cloudorch.modules.forecasting.ForecastingMain configs/forecast-default.properties
```

### Module B — Cost-Aware Predictive Orchestration

```bash
java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar orchestrate configs/orchestration-default.properties

java -cp target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar \
     in.ac.iitp.cloudorch.modules.orchestration.OrchestrationMain configs/orchestration-default.properties

# variants
java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar orchestrate configs/orchestration-hourly-billing.properties
java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar orchestrate configs/orchestration-slow-provisioning.properties
```

On Windows `cmd`, put the whole command on one line (no `\`).

### Run everything

```bash
./scripts/run-all.sh          # Linux/macOS
scripts\run-all.cmd           # Windows
```

### Overriding parameters without editing files

Any `key=value` argument after the config file overrides that key. This is useful for parameter sweeps:

```bash
JAR=target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar
java -jar $JAR orchestrate configs/orchestration-default.properties experiment.seeds=1,2,3 \
     policy.predictive.forecaster=WMA:6 pool.provisioningDelaySeconds=120 output.dir=results/sweep-wma6
java -jar $JAR forecast configs/forecast-default.properties forecast.models=SMA:4,EMA:0.5 forecast.horizons=1,2,4
```

Invalid values stop the run with exit code 2 and a clear message, for example: `configuration error: Config key 'pool.maxWorkers' must be an integer but was 'abc'`.

### Replaying a trace instead of synthetic load

Both modules accept per-interval arrival counts from a CSV: either one numeric column, or a header row that contains the named column. For example, you can feed Module A's generated series into Module B:

```bash
java -jar $JAR orchestrate configs/orchestration-default.properties \
     workload.traceFile=results/forecasting/forecast_series_seed42.csv \
     workload.traceColumn=actual workload.traceIntervalSeconds=300 \
     experiment.seeds=42 policies=REACTIVE,PREDICTIVE output.dir=results/replay
```

The same mechanism can load counts aggregated from a public cluster trace. No such trace is bundled here.

---

## 5. Design of the shared framework

### 5.1 Simulation model (`SimulationEngine`)

The engine is a deterministic discrete-event simulation. A priority queue of events is ordered by (time, sequence number). The event types are task arrival, task completion, worker ready, scheduled worker termination, and control tick.

* **Tasks** arrive at times taken from a `WorkloadTrace` and wait in a single FIFO queue. Each **worker** runs one task at a time, non-preemptively. When several workers are idle, the lowest-id worker gets the next task.
* Every `control.intervalSeconds` the **ScalingPolicy** receives a `ControlObservation`. It contains only monitoring data a real autoscaler would have: arrivals and completions in the last interval, queue length, committed/ready/provisioning/busy workers, utilization over the interval, and the observed mean service time. The policy never sees future arrivals.
* **Scale-out** launches workers that become ready after `pool.provisioningDelaySeconds`. Before launching, it reclaims workers that are already marked for removal, because they are still running and paid for.
* **Scale-in** removes, in order: workers still provisioning (newest first), then idle workers, then busy workers. Busy workers are marked *draining*: they finish their current task and then terminate.
* **Cost-aware scale-in** (`PREDICTIVE_COST_AWARE`) does not terminate a worker at once. It schedules termination at the end of the worker's already-paid billing block. Until then the worker keeps serving tasks, and a later scale-out can reclaim it. Workers closest to their billing boundary are chosen first.
* Workers present at t = 0 are pre-warmed.
* **End of run:** the control loop stops at the first tick at or after the workload window ends. Any remaining queue is drained by the workers present at that moment. Each worker is released as soon as it has nothing left to do. Tasks not finished within `simulation.maxDrainSeconds` are reported as incomplete.

### 5.2 Synthetic workload (`WorkloadGenerator`)

Arrivals form a non-homogeneous Poisson process, generated by Lewis–Shedler thinning, with this rate:

```
rate(t) = baseRate · max(0, 1 + trendPerHour·t/3600)
        · (1 + diurnalAmplitude · sin(2πt/diurnalPeriodSeconds + phase))
        · (burstMultiplier while a random burst is active, else 1)
```

At each whole minute a burst starts with probability `burstsPerHour/60` and lasts `burstDurationSeconds`. Service times are CONSTANT, EXPONENTIAL or LOGNORMAL, parameterised by mean and coefficient of variation.

### 5.3 Reproducibility

Each experiment seed creates a `RandomSource`. Independent named streams (`workload` → `arrivals`, `bursts`, `service`) are derived from it deterministically, so one component drawing more numbers never perturbs another. For each seed, **one** trace is generated and replayed for every policy (common random numbers). Policy differences therefore come from the policies, not from different workloads. Simulated time is independent of wall-clock time, and the effective configuration is saved next to every result set.

### 5.4 Forecasters

| Spec | Model |
|---|---|
| `NAIVE` | last observed value |
| `SMA:w` | mean of the last *w* values (mean of all values before the window fills) |
| `WMA:w` | linearly weighted mean of the last *w* values; the newest value has weight *w* |
| `EMA:α` | `s ← α·x + (1−α)·s`, initialised with the first value |

All four give a flat forecast: the same value for every horizon.

### 5.5 Scaling policies (Module B)

* **FIXED:n** keeps *n* workers at all times and starts with *n*.
* **REACTIVE** uses threshold/step rules. It adds `scaleOutStep` workers if utilization ≥ `upperUtilization` or queued tasks per ready worker ≥ `queuePerWorkerThreshold`. It removes `scaleInStep` workers if utilization ≤ `lowerUtilization` and the queue is empty. Separate cooldowns (in intervals) apply to scale-out and scale-in.
* **PREDICTIVE** feeds each interval's arrival count to the forecaster and then sizes the pool:

  ```
  λ        = forecastArrivals / intervalSeconds          (tasks/s)
  S        = observed mean service time (initial guess until the first completion)
  required = ceil( λ·S / targetUtilization · (1 + safetyMargin)
                   + queueLength·S / (intervalSeconds · backlogDrainIntervals) )
  ```

  λ·S is the offered load in busy workers (Little's law). Dividing by the target utilization leaves headroom for queueing, and the second term adds capacity to clear the existing backlog. Scale-in is held for `scaleInCooldownIntervals` after any increase.
* **PREDICTIVE_COST_AWARE** uses the same sizing as PREDICTIVE, plus cost-aware scale-in (§5.1).

### 5.6 Cost model

Each worker is billed from launch, including provisioning, to termination. Billed time is rounded **up** to whole `cost.billingGranularitySeconds` blocks and is never less than `cost.minimumBilledSeconds`. Cost = billed hours × `cost.pricePerWorkerHour`. Prices are in an abstract unit and are not taken from any provider's price list.

### 5.7 Metric definitions

| Metric | Definition |
|---|---|
| Queue wait | start time − arrival time |
| Latency (response time) | completion time − arrival time |
| p50 / p95 / p99 | nearest-rank percentile over all tasks of the run |
| SLA violation % | (tasks with latency > `sla.latencySeconds` + incomplete tasks) / arrived tasks |
| Throughput | completed tasks / makespan. The makespan runs from 0 until the last completion (at least the workload duration) |
| Worker-hours | total alive time of all workers, including provisioning, ÷ 3600 |
| Billed worker-hours, cost | after per-worker rounding by the cost model |
| Utilization | busy worker-seconds / ready worker-seconds |
| Forecast error | one-step-ahead MAE, RMSE, MAPE (and sMAPE and bias in Module A). MAPE skips intervals with zero actual arrivals |

In Module B, forecast error is recorded only for predictive policies. It compares the forecast made at the end of interval *k* with the actual arrivals in interval *k + 1*.

---

## 6. Configuration reference

| Key | Default | Meaning |
|---|---|---|
| `experiment.seeds` | `42` | comma-separated seeds; one independent workload per seed |
| `output.dir` | `results/<module>` | output folder (created if missing) |
| `workload.durationSeconds` | 7200 | length of the arrival window |
| `workload.baseRate` | 4.0 | tasks/s before modulation |
| `workload.diurnalAmplitude` / `PeriodSeconds` / `Phase` | 0.5 / 3600 / 0 | periodic component |
| `workload.trendPerHour` | 0 | relative linear growth per hour |
| `workload.burstsPerHour` / `burstMultiplier` / `burstDurationSeconds` | 0 / 1 / 0 | random bursts |
| `workload.service.type` / `meanSeconds` / `cv` | LOGNORMAL / 2.0 / 0.5 | service-time distribution |
| `workload.traceFile` / `traceColumn` / `traceIntervalSeconds` | – / `arrivals` / 60 | replay counts from CSV |
| `forecast.intervalSeconds` | 300 | (A) aggregation interval |
| `forecast.models` | NAIVE,SMA:3,SMA:6,SMA:12,WMA:6,EMA:0.30 | (A) models to compare |
| `forecast.horizons` | 1 | (A) steps ahead |
| `forecast.warmupIntervals` | auto = max window + max horizon − 1 | (A) intervals excluded from scoring |
| `output.series` | true | (A) write per-interval predictions |
| `policies` | FIXED,REACTIVE,PREDICTIVE,PREDICTIVE_COST_AWARE | (B) policy specs; `FIXED:n` allowed |
| `control.intervalSeconds` | 60 | (B) monitoring/decision period |
| `pool.initialWorkers` / `minWorkers` / `maxWorkers` | 8 / 1 / 40 | (B) pool limits |
| `pool.provisioningDelaySeconds` | 60 | (B) cold-start delay |
| `simulation.maxDrainSeconds` | 3600 | (B) drain limit after the window |
| `cost.pricePerWorkerHour` / `billingGranularitySeconds` / `minimumBilledSeconds` | 0.10 / 60 / 60 | (B) billing |
| `sla.latencySeconds` | 10 | (B) SLA threshold |
| `policy.fixed.workers` | `pool.initialWorkers` | (B) size used by plain `FIXED` |
| `policy.reactive.*` | 0.80 / 0.40 / 2.0 / 4 / 2 / 1 / 3 | upper util, lower util, queue/worker, out step, in step, out cooldown, in cooldown |
| `policy.predictive.forecaster` | SMA:5 | any forecaster spec |
| `policy.predictive.targetUtilization` / `safetyMargin` | 0.75 / 0.10 | sizing headroom |
| `policy.predictive.backlogDrainIntervals` | 1.0 | intervals allowed to clear the backlog |
| `policy.predictive.lookaheadIntervals` | ceil(provisioningDelay / interval) | forecast horizon used for sizing |
| `policy.predictive.scaleInCooldownIntervals` | 2 | hold after an increase |
| `output.taskRecords` | false | (B) one CSV row per task (large) |

---

## 7. Output files

**Module A** (`results/forecasting/`):

* `forecast_summary.csv`: one row per seed × model × horizon, with points, MAE, RMSE, MAPE %, sMAPE % and bias.
* `forecast_aggregate.csv`: mean and sample std of each metric across seeds.
* `forecast_series_seed<S>.csv`: each interval's actual arrivals, a flag for whether the interval is in the evaluation window, and every model's prediction. Use this file to plot forecast against actual.
* `effective-config.properties`: the exact configuration used.

**Module B** (`results/orchestration*/`):

* `orchestration_summary.csv`: one row per seed × policy with every metric in §5.7.
* `orchestration_aggregate.csv`: mean and std across seeds.
* `orchestration_intervals_seed<S>.csv`: per-interval time series for every policy (arrivals, forecast, target/committed/ready/provisioning workers, queue length, utilization, accrued worker-hours). Use it for capacity-vs-demand plots.
* `effective-config.properties`, and optionally `tasks_seed<S>_<policy>.csv`.

Empty CSV cells mean "not applicable", for example forecast error for a non-forecasting policy.

---

## 8. Sample output (real runs, not edited)

These excerpts come from the runs saved in `sample-output/`. The environment was OpenJDK 21.0.12 on Linux, and the jar was built with `javac --release 17`. Each sample configuration runs in about 1 second of wall-clock time.

On the same JDK and platform, the same configuration and seeds give byte-identical CSVs. The simulator's own transcendental functions use `StrictMath`, which gives bit-exact results everywhere. However, the JDK's Gaussian sampler, used for log-normal service times, is not specified to be bit-exact across JDK versions. A different JDK could therefore produce slightly different numbers, although that is unlikely. If you need cross-JDK identity, use `EXPONENTIAL` or `CONSTANT` service times.

### Module A: `forecast-default` (5 seeds, 1 simulated day, 5-minute intervals, 274 scored intervals per seed)

```
Forecast error, mean +/- sample std over seeds, sorted by RMSE (horizon 1 interval(s)):
model (h=1) |             MAE |            RMSE |         MAPE % |        sMAPE % |          bias
------------+-----------------+-----------------+----------------+----------------+--------------
NAIVE       | 178.1 +/- 37.03 | 387.4 +/- 62.72 | 11.38 +/- 1.57 | 10.80 +/- 1.44 | 0.35 +/- 0.97
EMA(0.60)   | 206.0 +/- 39.14 | 398.1 +/- 54.76 | 13.19 +/- 1.62 | 12.68 +/- 1.49 | 0.37 +/- 1.48
EMA(0.30)   | 238.8 +/- 41.43 | 407.0 +/- 50.88 | 15.43 +/- 1.58 | 15.25 +/- 1.49 | 0.29 +/- 2.20
WMA(12)     | 263.8 +/- 40.69 | 421.7 +/- 49.29 | 17.20 +/- 1.45 | 17.20 +/- 1.38 | 0.26 +/- 2.69
WMA(3)      | 219.0 +/- 39.31 | 422.8 +/- 54.02 | 14.09 +/- 1.62 | 13.39 +/- 1.48 | 0.41 +/- 1.56
WMA(6)      | 245.6 +/- 44.05 | 425.1 +/- 52.87 | 15.87 +/- 1.70 | 15.51 +/- 1.61 | 0.27 +/- 2.05
SMA(12)     | 278.6 +/- 36.34 | 432.4 +/- 43.72 | 18.36 +/- 1.40 | 18.43 +/- 1.31 | 0.22 +/- 3.44
SMA(6)      | 267.4 +/- 47.69 | 441.3 +/- 56.19 | 17.39 +/- 1.79 | 17.12 +/- 1.69 | 0.18 +/- 2.40
SMA(3)      | 241.2 +/- 40.71 | 450.1 +/- 51.91 | 15.60 +/- 1.66 | 14.81 +/- 1.54 | 0.41 +/- 1.82

Forecast error ... (horizon 3 interval(s)):
model (h=3) |             MAE |            RMSE |         MAPE % |        sMAPE % |          bias
------------+-----------------+-----------------+----------------+----------------+--------------
SMA(12)     | 299.1 +/- 34.55 | 456.6 +/- 39.88 | 20.08 +/- 1.45 | 20.13 +/- 1.33 | 0.15 +/- 4.30
WMA(12)     | 297.3 +/- 40.19 | 461.4 +/- 47.08 | 19.71 +/- 1.45 | 19.74 +/- 1.35 | 0.18 +/- 3.51
...
NAIVE       | 314.3 +/- 41.68 | 588.0 +/- 58.77 | 20.68 +/- 1.67 | 19.11 +/- 1.59 | 0.31 +/- 2.51
```

### Module B: `orchestration-default` (5 seeds, 2 h, per-minute billing, 60 s cold start)

```
policy                |     mean wait s |      p95 wait s |   p95 latency s |      SLA viol % |      thruput/s |        worker-h |    billed cost |            util | forecast MAPE %
----------------------+-----------------+-----------------+-----------------+-----------------+----------------+-----------------+----------------+-----------------+----------------
FIXED-12              | 217.8 +/- 188.0 | 575.8 +/- 394.4 | 577.7 +/- 394.2 | 66.00 +/- 24.40 | 5.00 +/- 0.642 | 24.09 +/- 0.206 | 2.42 +/- 0.022 | 0.834 +/- 0.107 |             n/a
FIXED-32              | 0.109 +/- 0.126 |  0.919 +/- 1.15 |  4.32 +/- 0.545 | 0.022 +/- 0.018 | 5.02 +/- 0.681 | 64.00 +/- 0.003 | 6.42 +/- 0.008 | 0.314 +/- 0.043 |             n/a
REACTIVE              |   5.34 +/- 4.75 | 40.90 +/- 34.00 | 43.09 +/- 33.69 |  13.20 +/- 7.70 | 5.02 +/- 0.681 |  45.64 +/- 9.16 | 4.58 +/- 0.921 | 0.455 +/- 0.043 |             n/a
PREDICTIVE[SMA(3)]    |   5.50 +/- 2.94 | 30.80 +/- 16.11 | 33.16 +/- 15.46 |  18.67 +/- 9.70 | 5.02 +/- 0.681 |  31.48 +/- 4.47 | 3.17 +/- 0.448 | 0.674 +/- 0.006 |  21.71 +/- 4.67
PREDICTIVE-CA[SMA(3)] |   5.50 +/- 2.94 | 30.80 +/- 16.11 | 33.16 +/- 15.46 |  18.67 +/- 9.70 | 5.02 +/- 0.681 |  31.48 +/- 4.47 | 3.17 +/- 0.448 | 0.674 +/- 0.006 |  21.71 +/- 4.67
```

### Module B: `orchestration-hourly-billing` (same workload, 1-hour billing blocks)

```
policy                |     mean wait s |      p95 wait s |   p95 latency s |      SLA viol % |      thruput/s |        worker-h |    billed cost |            util | forecast MAPE %
----------------------+-----------------+-----------------+-----------------+-----------------+----------------+-----------------+----------------+-----------------+----------------
FIXED-12              | 217.8 +/- 188.0 | 575.8 +/- 394.4 | 577.7 +/- 394.2 | 66.00 +/- 24.40 | 5.00 +/- 0.642 | 24.09 +/- 0.206 | 3.26 +/- 0.351 | 0.834 +/- 0.107 |             n/a
FIXED-32              | 0.109 +/- 0.126 |  0.919 +/- 1.15 |  4.32 +/- 0.545 | 0.022 +/- 0.018 | 5.02 +/- 0.681 | 64.00 +/- 0.003 | 7.34 +/- 0.472 | 0.314 +/- 0.043 |             n/a
REACTIVE              |   5.34 +/- 4.75 | 40.90 +/- 34.00 | 43.09 +/- 33.69 |  13.20 +/- 7.70 | 5.02 +/- 0.681 |  45.64 +/- 9.16 |  8.30 +/- 1.78 | 0.455 +/- 0.043 |             n/a
PREDICTIVE[SMA(3)]    |   5.50 +/- 2.94 | 30.80 +/- 16.11 | 33.16 +/- 15.46 |  18.67 +/- 9.70 | 5.02 +/- 0.681 |  31.48 +/- 4.47 | 11.96 +/- 3.69 | 0.674 +/- 0.006 |  21.71 +/- 4.67
PREDICTIVE-CA[SMA(3)] |   1.87 +/- 1.40 | 17.40 +/- 13.17 | 19.81 +/- 12.65 |   6.15 +/- 5.08 | 5.02 +/- 0.681 | 55.64 +/- 14.42 |  6.54 +/- 1.66 | 0.375 +/- 0.049 |  21.71 +/- 4.67
```

The full transcripts, including per-seed lines and the slow-provisioning variant, are in `sample-output/*.console.txt`.

### What these particular runs show (and do not show)

These are observations about **these synthetic configurations with 5 seeds**. They are not general conclusions. With 5 seeds the standard deviations are large, and no significance tests were run.

1. **Under- vs over-provisioning.** FIXED-12 is the cheapest, but its queue grows during peaks (p95 latency ≈ 578 s). FIXED-32 almost never queues, but it costs about 2.6× more and runs at ≈ 31 % utilization. The adaptive policies sit between the two extremes.
2. **Predictive vs reactive (per-minute billing).** PREDICTIVE used fewer worker-hours (31.5 vs 45.6) and had lower mean p95 latency (33 vs 43 s) than REACTIVE. However, its SLA-violation percentage was *higher* (18.7 % vs 13.2 %), and the spread across seeds overlaps. Neither policy dominates on every metric.
3. **Cost-aware scale-in only matters with coarse billing.** With 60-second blocks, PREDICTIVE-CA is identical to PREDICTIVE. Launches happen on 60-second control ticks, so every billing boundary falls exactly on a tick and nothing is ever deferred. With 1-hour blocks, plain PREDICTIVE became the *most* expensive policy (11.96): each scale-in/scale-out cycle starts a new paid hour. The cost-aware variant reduced cost to 6.54 and also lowered latency, because it keeps paid-for workers serving.
4. **Moving averages lag bursts.** In Module A, NAIVE has the lowest RMSE at a 1-step horizon, because bursts last two intervals and the last value is the best guess. At a 3-step horizon, longer windows (SMA/WMA 12) do best. In `orchestration-slow-provisioning` (3-minute cold starts), PREDICTIVE[SMA(3)] had *worse* latency than REACTIVE (p95 ≈ 99 s vs 51 s). A flat moving-average forecast cannot anticipate a ramp three intervals ahead. This is a limitation of the forecaster, not a bug, and it motivates trend-aware forecasters (§12).

---

## 9. Demonstrating the prototype in a viva (≈ 10–12 minutes)

1. **Show the build is real (1 min).** Run `mvn clean verify` and point to the `Tests run: 66, Failures: 0` summary. Show the jar in `target/`.
2. **Explain the architecture (2 min).** Use the tree in §2. The key points:
   * the framework is policy-agnostic: `ScalingPolicy` and `Forecaster` are the only extension points;
   * the modules depend only on the framework, and `ModuleIndependenceTest` enforces this.
3. **Run Module A (2 min).**
   `java -jar target/...jar forecast configs/forecast-default.properties`
   Explain the walk-forward evaluation (no look-ahead), the warm-up, and why NAIVE wins at h=1 but long windows win at h=3. Then open `results/forecasting/forecast_series_seed42.csv` in a spreadsheet and plot `actual` against `SMA(12)@h3`.
4. **Run Module B (3 min).**
   `java -jar target/...jar orchestrate configs/orchestration-default.properties`
   Walk through the table: the cost/latency trade-off between the two fixed baselines, and where the reactive and predictive policies fall. Open `orchestration_intervals_seed43.csv`, filter `policy`, and plot `arrivals` and `ready_workers` over `interval` for REACTIVE and PREDICTIVE. This shows how each policy's capacity follows demand.
5. **Show the cost-aware effect (2 min).** Run `orchestration-hourly-billing.properties`. Explain why CA makes no difference with per-minute billing and a large difference with hourly billing (§8, point 3).
6. **Show reproducibility (1 min).** Re-run any command, then compare the CSVs with `diff` or `fc` to show they are identical. Then change `experiment.seeds=7` to show a different, but again reproducible, result.
7. **Live parameter change (optional).** For example, `policy.predictive.forecaster=WMA:6 pool.provisioningDelaySeconds=180`. Tie the result back to the limitation in §8, point 4.

**Questions to prepare for, and where the answers are:**

* *Why simulation and not real VMs?* Reproducibility, zero cost, and full control of cold-start and billing parameters (§10, §11).
* *How is p95 computed?* Nearest-rank (§5.7; `StatisticsTest`).
* *How do you know the simulator is correct?* Hand-computed scenarios and exact conservation laws are tested: ∫queue length dt = Σ waits, ∫busy workers dt = Σ service times, and identical outputs for identical seeds (`SimulationEngineTest`).
* *Why does the predictive policy lose in the slow-provisioning case?* §8, point 4.
* *What is "cost-aware" about the policy?* Billing-boundary-aware scale-in and reclaiming of paid workers (§5.1, §5.5).

---

## 10. Assumptions

* Workers are homogeneous. A task's service time does not depend on the worker or on the load (no interference or contention), and there are no worker failures.
* There is one FIFO queue with no task priorities, deadlines or dependencies. Task sizes are not known to the scheduler.
* Monitoring is perfect and instantaneous at each control tick. Scaling decisions are applied immediately, and only workers are delayed, by the provisioning delay.
* Workers present at t = 0 are pre-warmed. There is no limit on concurrent launches other than `pool.maxWorkers`.
* Cost includes compute time only: no storage, network egress, licensing, spot/reserved pricing or discounts. Prices are abstract units.
* Network latency between the queue and the workers is ignored.
* The synthetic workload parameters (rates, burst sizes, cycle periods, 2-second log-normal service times) are illustrative choices. They are **not** fitted to any real system.
* The 1-hour "day" in Module B compresses a diurnal cycle into the 2-hour experiment window, so the policies see two full cycles.

## 11. Limitations and threats to validity

* **No real-cloud validation.** All results come from the simulator. They have not been compared with measurements on AWS, Azure, GCP, Kubernetes or any real cluster. Absolute numbers (latency in seconds, cost units) mean nothing outside this model. Only the comparisons *within* a configuration are informative, and only under the assumptions above.
* **Synthetic workloads only.** No real trace is bundled. Trace replay is supported (§4), but it has only been exercised with the simulator's own generated series.
* **Small number of seeds.** The sample configs use 5 seeds. Results are reported as mean ± sample standard deviation, without confidence intervals or hypothesis tests. Claims of superiority would need more seeds and proper statistics.
* **Untuned parameters.** Policy parameters (thresholds, cooldowns, target utilization, window sizes) were set to reasonable values. They were not optimised, and results are sensitive to them. A sweep (§4) is the right way to study this.
* **Simple forecasters.** Moving averages produce flat forecasts with no trend or seasonality, so they lag ramps and bursts. Forecast error in Module B is the one-step-ahead error, even when the sizing uses a longer lookahead (for flat forecasters the values are identical).
* **End-of-run handling.** After the workload window, scaling stops and the backlog is drained by the existing workers. A policy that ends the window under-provisioned (for example FIXED-12) therefore has a long drain phase, which affects its makespan and throughput.
* **Single-threaded simulation.** The "worker pool" is simulated, not a real thread pool. The prototype measures scheduling and scaling behaviour, not JVM or OS performance.
* **Build verification.** As stated in §1, `mvn clean verify` could not be executed in the development environment.

This is a **research prototype**. It is not production-ready and has not been security-reviewed, hardened or load-tested as a service.

## 12. Possible extensions

* Trend- and seasonality-aware forecasters (Holt, Holt–Winters, ARIMA, or an LSTM via an external library) behind the existing `Forecaster` interface.
* Cost-aware *sizing* (an explicit cost/SLA objective) in addition to cost-aware scale-in. Spot instances with interruption.
* Heterogeneous worker types, task classes with deadlines, and multi-queue schedulers.
* Replay of public cluster traces (aggregate the task arrivals to per-interval counts and load them with `workload.traceFile`).
* Statistical analysis scripts (confidence intervals, paired tests across seeds), and plots generated from the CSVs.
* A thin Spring Boot / web dashboard on top of the same framework, if an interactive demo is needed.
