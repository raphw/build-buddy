package build;

import build.buildbuddy.BuildExecutor;
import build.buildbuddy.BuildExecutorCallback;
import build.buildbuddy.HashDigestFunction;
import build.buildbuddy.Repository;
import build.buildbuddy.Resolver;
import build.buildbuddy.maven.MavenDefaultRepository;
import build.buildbuddy.maven.MavenPomResolver;
import build.buildbuddy.maven.MavenUriParser;
import build.buildbuddy.module.DownloadModuleUris;
import build.buildbuddy.module.ModularJarResolver;
import build.buildbuddy.module.ModularProject;
import build.buildbuddy.project.JavaModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModularByMaven {

    public static void main(String[] args) throws IOException {
        BuildExecutor root = BuildExecutor.of(Path.of("target"),
                new HashDigestFunction("MD5"),
                BuildExecutorCallback.printing(System.out));
        root.addStep("download", new DownloadModuleUris("module", List.of(
                DownloadModuleUris.DEFAULT,
                Path.of("dependencies/modules.properties").toUri())));
        root.addModule("build", (build, downloaded) -> {
            Map<String, Repository> repositories = Map.of("maven", new MavenDefaultRepository());
            build.addModule("modules", ModularProject.make(
                    Path.of("."),
                    "SHA256",
                    Resolver.translate("module",
                            MavenUriParser.ofUris(new MavenUriParser(), DownloadModuleUris.URIS, downloaded.values()),
                            Map.of("module", new ModularJarResolver(false), "maven", new MavenPomResolver()),
                            repositories),
                    repositories,
                    (_, _) -> (buildExecutor, inherited) -> buildExecutor.addModule("java",
                            new JavaModule().testIfAvailable(),
                            Stream.concat(Stream.of("../dependencies/artifacts"), inherited.sequencedKeySet().stream()
                                    .filter(identity -> identity.startsWith("../../../"))).collect(
                                    Collectors.toCollection(LinkedHashSet::new)))));
        }, "download");
        root.execute();
    }
}
