package build.buildbuddy.test.module;

import build.buildbuddy.BuildExecutor;
import build.buildbuddy.BuildStep;
import build.buildbuddy.HashDigestFunction;
import build.buildbuddy.module.ModularProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.SequencedMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ModularProjectTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path project, build;

    @Before
    public void setUp() throws Exception {
        project = temporaryFolder.newFolder("build/buildbuddy/project").toPath();
        build = temporaryFolder.newFolder("build").toPath();
    }

    @Test
    public void can_resolve_module() throws IOException {
        Files.writeString(project.resolve("module-info.java"), """
                module foo {
                  requires bar;
                }
                """);
        BuildExecutor executor = BuildExecutor.of(build, new HashDigestFunction("MD5"));
        executor.addModule("module", new ModularProject("module", project, _ -> true));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("module/module-/source", "module/module-/module");
        assertThat(results.get("module/module-/source").resolve(BuildStep.SOURCES + "module-info.java")).exists();
        Path module = results.get("module/module-/module");
        assertThat(module.resolve(BuildStep.COORDINATES)).exists();
        Properties coordinates = new Properties();
        try (Reader reader = Files.newBufferedReader(module.resolve(BuildStep.COORDINATES))) {
            coordinates.load(reader);
        }
        assertThat(coordinates).containsOnlyKeys("module/foo");
        assertThat(coordinates.getProperty("module/foo")).isEmpty();
        assertThat(module.resolve(BuildStep.DEPENDENCIES)).exists();
        Properties dependencies = new Properties();
        try (Reader reader = Files.newBufferedReader(module.resolve(BuildStep.DEPENDENCIES))) {
            dependencies.load(reader);
        }
        assertThat(dependencies).containsOnlyKeys("module/bar");
        assertThat(dependencies.getProperty("module/bar")).isEmpty();
    }
}
