package in.ac.iitp.cloudorch.framework.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Shared command-line handling: {@code <config.properties> [key=value ...]}. */
public final class CliSupport {

    /** Body of a module run. */
    @FunctionalInterface
    public interface ExperimentBody {
        void run(ExperimentConfig config) throws Exception;
    }

    private CliSupport() {
    }

    /** Parses arguments, runs the body, and returns a process exit code. */
    public static int run(String moduleName, String[] args, ExperimentBody body) {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            System.err.println("usage: " + moduleName + " <config.properties> [key=value ...]");
            System.err.println("  key=value pairs override entries in the config file, e.g. experiment.seeds=1,2,3");
            return args.length == 0 ? 2 : 0;
        }
        try {
            Path file = Path.of(args[0]);
            if (!Files.isRegularFile(file)) {
                System.err.println("config file not found: " + file.toAbsolutePath());
                return 2;
            }
            List<String> overrides = Arrays.asList(args).subList(1, args.length);
            ExperimentConfig cfg = ExperimentConfig.load(file).withOverrides(overrides);
            long t0 = System.nanoTime();
            body.run(cfg);
            System.out.printf("finished in %.2f s (wall clock)%n", (System.nanoTime() - t0) / 1e9);
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("configuration error: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("run failed: " + e);
            e.printStackTrace(System.err);
            return 1;
        }
    }
}
