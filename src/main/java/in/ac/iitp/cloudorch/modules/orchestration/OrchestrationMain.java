package in.ac.iitp.cloudorch.modules.orchestration;

import in.ac.iitp.cloudorch.framework.config.CliSupport;

/**
 * Stand-alone entry point for Module B.
 *
 * <pre>
 * java -cp target/cloud-orchestration-sim-0.1.0-SNAPSHOT.jar \
 *      in.ac.iitp.cloudorch.modules.orchestration.OrchestrationMain configs/orchestration-default.properties [key=value ...]
 * </pre>
 */
public final class OrchestrationMain {

    private OrchestrationMain() {
    }

    public static void main(String[] args) {
        System.exit(CliSupport.run("orchestrate", args, cfg -> new OrchestrationExperiment(cfg, System.out).run()));
    }
}
