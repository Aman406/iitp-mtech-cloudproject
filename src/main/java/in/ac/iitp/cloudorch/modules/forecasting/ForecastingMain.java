package in.ac.iitp.cloudorch.modules.forecasting;

import in.ac.iitp.cloudorch.framework.config.CliSupport;

/**
 * Stand-alone entry point for Module A.
 *
 * <pre>
 * java -cp target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar \
 *      in.ac.iitp.cloudorch.modules.forecasting.ForecastingMain configs/forecast-default.properties [key=value ...]
 * </pre>
 */
public final class ForecastingMain {

    private ForecastingMain() {
    }

    public static void main(String[] args) {
        System.exit(CliSupport.run("forecast", args, cfg -> new ForecastingExperiment(cfg, System.out).run()));
    }
}
