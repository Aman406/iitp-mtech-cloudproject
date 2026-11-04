package in.ac.iitp.cloudorch;

import in.ac.iitp.cloudorch.modules.forecasting.ForecastingMain;
import in.ac.iitp.cloudorch.modules.orchestration.OrchestrationMain;

import java.util.Arrays;

/**
 * Convenience launcher for the executable jar. Each module also has its own main class and can be
 * run without this launcher.
 *
 * <pre>
 * java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar forecast    configs/forecast-default.properties
 * java -jar target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar orchestrate configs/orchestration-default.properties
 * </pre>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "forecast" -> ForecastingMain.main(rest);
            case "orchestrate" -> OrchestrationMain.main(rest);
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: java -jar cloud-orchestration-sim.jar <forecast|orchestrate> <config.properties> [key=value ...]");
        System.err.println("  forecast     Module A: evaluate moving-average workload forecasters");
        System.err.println("  orchestrate  Module B: compare fixed, reactive and predictive worker scaling");
    }
}
