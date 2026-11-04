package in.ac.iitp.cloudorch.framework.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentConfigTest {

    @Test
    void loadsFileAndAppliesOverrides() throws IOException {
        Path f = Files.createTempFile("cfg", ".properties");
        Files.write(f, List.of("# comment", "a.int = 5", "a.list=1, 2,3", "a.flag=true", "a.empty="));
        ExperimentConfig c = ExperimentConfig.load(f).withOverrides(List.of("a.int=7", "b.new=x"));
        assertEquals(7, c.getInt("a.int", 0));
        assertEquals(List.of(1L, 2L, 3L), c.getLongList("a.list", List.of()));
        assertTrue(c.getBoolean("a.flag", false));
        assertEquals("x", c.getString("b.new", "?"));
        assertFalse(c.has("a.empty"));
        assertEquals(9, c.getInt("a.empty", 9)); // empty value falls back to default
    }

    @Test
    void reportsBadValuesClearly() {
        ExperimentConfig c = ExperimentConfig.of(Map.of("n", "abc", "b", "yes"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> c.getInt("n", 0));
        assertTrue(e.getMessage().contains("'n'"));
        assertThrows(IllegalArgumentException.class, () -> c.getBoolean("b", false));
        assertThrows(IllegalArgumentException.class, () -> c.withOverrides(List.of("novalue")));
    }

    @Test
    void writesEffectiveConfig() throws IOException {
        Path dir = Files.createTempDirectory("cfgout");
        Path out = dir.resolve("sub/effective.properties");
        ExperimentConfig.of(Map.of("k", "v")).writeTo(out, "test");
        assertTrue(Files.readString(out).contains("k=v"));
    }
}
