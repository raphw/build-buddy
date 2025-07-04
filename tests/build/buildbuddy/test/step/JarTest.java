package build.buildbuddy.test.step;

import build.buildbuddy.BuildStep;
import build.buildbuddy.BuildStepArgument;
import build.buildbuddy.BuildStepContext;
import build.buildbuddy.BuildStepResult;
import build.buildbuddy.ChecksumStatus;
import build.buildbuddy.step.Jar;
import build.buildbuddy.step.Javac;
import sample.Sample;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class JarTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, classes;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        classes = Files.createDirectory(root.resolve("classes"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_jarl(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(folder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        BuildStepResult result = (process ? Jar.process() : Jar.tool()).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.class"), ChecksumStatus.ADDED))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(BuildStep.ARTIFACTS + "classes.jar")).isNotEmptyFile();
    }
}
