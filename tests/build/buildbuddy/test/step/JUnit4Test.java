package build.buildbuddy.test.step;

import build.buildbuddy.BuildStep;
import build.buildbuddy.BuildStepArgument;
import build.buildbuddy.BuildStepContext;
import build.buildbuddy.BuildStepResult;
import build.buildbuddy.ChecksumStatus;
import build.buildbuddy.step.JUnit4;
import build.buildbuddy.step.Javac;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.JUnitCore;
import sample.TestSample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class JUnit4Test {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path previous;
    private Path next;
    private Path supplement;
    private Path dependencies;
    private Path classes;

    @Before
    public void setUp() throws Exception {
        Path root = temporaryFolder.newFolder("root").toPath();
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        dependencies = Files.createDirectories(root.resolve("dependencies"));
        classes = Files.createDirectories(root.resolve("classes"));
        Path artifacts = Files.createDirectory(dependencies.resolve(BuildStep.ARTIFACTS));
        Files.copy(
                Path.of(JUnitCore.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
                artifacts.resolve("junit.jar"));
        Files.copy(
                Path.of(Class.forName("org.hamcrest.CoreMatchers").getProtectionDomain().getCodeSource().getLocation().toURI()),
                artifacts.resolve("hamcrest-core.jar"));
        try (InputStream input = TestSample.class.getResourceAsStream(TestSample.class.getSimpleName() + ".class");
             OutputStream output = Files.newOutputStream(Files
                     .createDirectories(classes.resolve(Javac.CLASSES + "sample"))
                     .resolve("TestSample.class"))) {
            requireNonNull(input).transferTo(output);
        }
    }

    @Test
    public void can_execute_junit4() throws IOException {
        BuildStepResult result = new JUnit4(candidate -> candidate.endsWith("TestSample")).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "dependencies", new BuildStepArgument(
                                dependencies,
                                Map.of(
                                        Path.of(BuildStep.ARTIFACTS + "junit.jar"), ChecksumStatus.ADDED,
                                        Path.of(BuildStep.ARTIFACTS + "hamcrest-core.jar"), ChecksumStatus.ADDED)),
                        "classes", new BuildStepArgument(
                                classes,
                                Map.of(Path.of(Javac.CLASSES + "sample/TestSample.class"), ChecksumStatus.ADDED))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("output")).content()
                .contains("JUnit")
                .contains(".Hello world!")
                .contains("OK (1 test)");
        assertThat(supplement.resolve("error")).isEmptyFile();
    }

    @Test
    public void can_execute_junit4_non_modular() throws IOException {
        BuildStepResult result = new JUnit4(candidate -> candidate.endsWith("TestSample")).modular(false).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "dependencies", new BuildStepArgument(
                                dependencies,
                                Map.of(
                                        Path.of(BuildStep.ARTIFACTS + "junit.jar"), ChecksumStatus.ADDED,
                                        Path.of(BuildStep.ARTIFACTS + "hamcrest-core.jar"), ChecksumStatus.ADDED)),
                        "classes", new BuildStepArgument(
                                classes,
                                Map.of(Path.of(Javac.CLASSES + "sample/TestSample.class"), ChecksumStatus.ADDED))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("output")).content()
                .contains("JUnit")
                .contains(".Hello world!")
                .contains("OK (1 test)");
        assertThat(supplement.resolve("error")).isEmptyFile();
    }
}
