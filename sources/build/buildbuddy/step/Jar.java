package build.buildbuddy.step;

import build.buildbuddy.BuildStepArgument;
import build.buildbuddy.BuildStepContext;
import build.buildbuddy.ProcessBuildStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class Jar implements ProcessBuildStep {

    public static final String JARS = "jars/";

    private final String jar;

    public Jar() {
        jar = ProcessBuildStep.ofJavaHome("bin/jar" + (WINDOWS ? ".exe" : ""));
    }

    public Jar(String jar) {
        this.jar = jar;
    }

    @Override
    public CompletionStage<ProcessBuilder> process(Executor executor,
                                                   BuildStepContext context,
                                                   Map<String, BuildStepArgument> arguments) throws IOException {
        List<String> commands = new ArrayList<>(List.of(jar,
                "cf",
                Files.createDirectory(context.next().resolve(JARS)).resolve("artifact.jar").toString()));
        for (BuildStepArgument argument : arguments.values()) {
            for (String name : List.of(Javac.CLASSES, Bind.RESOURCES)) {
                Path folder = argument.folder().resolve(name);
                if (Files.exists(folder)) {
                    commands.add(folder.toString());
                }
            }
        }
        return CompletableFuture.completedStage(new ProcessBuilder(commands));
    }
}
