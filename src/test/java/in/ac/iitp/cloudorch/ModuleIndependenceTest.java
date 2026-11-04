package in.ac.iitp.cloudorch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture check: the shared framework must not depend on any research module, and the two
 * research modules must not depend on each other. Runs against the source tree (Maven executes
 * tests with the project directory as the working directory).
 */
class ModuleIndependenceTest {

    private static final Path ROOT = Path.of("src/main/java/in/ac/iitp/cloudorch");
    private static final String PKG = "in.ac.iitp.cloudorch.";

    @Test
    void frameworkDoesNotDependOnModules() throws IOException {
        assertNoReference(ROOT.resolve("framework"), PKG + "modules.");
    }

    @Test
    void forecastingModuleDoesNotDependOnOrchestrationModule() throws IOException {
        assertNoReference(ROOT.resolve("modules/forecasting"), PKG + "modules.orchestration");
    }

    @Test
    void orchestrationModuleDoesNotDependOnForecastingModule() throws IOException {
        assertNoReference(ROOT.resolve("modules/orchestration"), PKG + "modules.forecasting");
    }

    private static void assertNoReference(Path dir, String forbidden) throws IOException {
        assertTrue(Files.isDirectory(dir), "source directory not found: " + dir.toAbsolutePath());
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(dir)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(forbidden);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
        assertTrue(offenders.isEmpty(), "unexpected dependency on " + forbidden + " in " + offenders);
    }
}
