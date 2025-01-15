package build.buildbuddy.test.project;

import build.buildbuddy.BuildExecutor;
import build.buildbuddy.BuildStep;
import build.buildbuddy.BuildStepResult;
import build.buildbuddy.HashDigestFunction;
import build.buildbuddy.project.MultiProjectModule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiProjectModuleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private BuildExecutor buildExecutor;

    @Before
    public void setUp() throws Exception {
        buildExecutor = BuildExecutor.of(
                temporaryFolder.newFolder("root").toPath(),
                new HashDigestFunction("MD5"));
    }

    @Test
    public void can_resolve_project() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            Path module1 = temporaryFolder.newFolder("module-1").toPath();
            Properties coordinates1 = new Properties();
            coordinates1.put("foo/bar", "");
            try (Writer writer = Files.newBufferedWriter(module1.resolve(BuildStep.COORDINATES))) {
                coordinates1.store(writer, null);
            }
            buildExecutor.addSource("module-1-module", module1);
            buildExecutor.addSource("module-1-source", Files.writeString(Files.createDirectory(temporaryFolder
                    .newFolder("source-1")
                    .toPath()
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            Path module2 = temporaryFolder.newFolder("module-2").toPath();
            Properties coordinates2 = new Properties();
            coordinates2.put("foo/qux", "");
            try (Writer writer = Files.newBufferedWriter(module2.resolve(BuildStep.COORDINATES))) {
                coordinates2.store(writer, null);
            }
            Properties dependencies2 = new Properties();
            dependencies2.put("foo/bar", "");
            try (Writer writer = Files.newBufferedWriter(module2.resolve(BuildStep.DEPENDENCIES))) {
                dependencies2.store(writer, null);
            }
            buildExecutor.addSource("module-2-module", module2);
            buildExecutor.addSource("module-2-source", Files.writeString(Files.createDirectory(temporaryFolder
                    .newFolder("source-2")
                    .toPath()
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
        }, identifier -> Optional.of(identifier.replace('-', '/')), modules -> {
            assertThat(modules).containsExactly(
                    Map.entry("1", new LinkedHashSet<>()),
                    Map.entry("2", new LinkedHashSet<>(Set.of("1"))));
            return (name, dependencies, identifiers) -> switch (name) {
                case "1" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identify/module/1/module",
                            "../../identify/module/1/source");
                    assertThat(dependencies).isEmpty();
                    yield (module1, inherited) -> module1.addStep("step", (_, context, _) -> {
                        assertThat(inherited).isEmpty();
                        Files.writeString(context.next().resolve("file"), "foo");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    });
                }
                case "2" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identify/module/2/module",
                            "../../identify/module/2/source");
                    assertThat(dependencies).containsExactly("1");
                    yield  (module2, inherited) -> {
                        assertThat(inherited).containsKeys("../1/step");
                        module2.addStep("step", (_, context, arguments) -> {
                            Files.writeString(
                                    context.next().resolve("file"),
                                    Files.readString(arguments.get("../1/step").folder().resolve("file")) + "bar");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }, "../1/step");
                    };
                }
                default -> throw new AssertionError();
            };
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths).containsKeys("project/build/module/2/step");
        assertThat(paths.get("project/build/module/2/step").resolve("file")).content().contains("foobar");
    }
}
