package build;

import build.buildbuddy.BuildExecutor;
import build.buildbuddy.Repository;
import build.buildbuddy.Resolver;
import build.buildbuddy.maven.MavenDefaultRepository;
import build.buildbuddy.maven.MavenPomResolver;
import build.buildbuddy.maven.MavenRepository;
import build.buildbuddy.step.Bind;
import build.buildbuddy.step.Download;
import build.buildbuddy.step.Jar;
import build.buildbuddy.step.Javac;
import build.buildbuddy.step.Resolve;
import build.buildbuddy.step.TestEngine;
import build.buildbuddy.step.Tests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class Manual {

    static void main(String[] args) throws IOException {
        MavenRepository mavenRepository = new MavenDefaultRepository();
        Map<String, Repository> repositories = Map.of("maven", mavenRepository);
        Map<String, Resolver> resolvers = Map.of("maven", new MavenPomResolver());

        BuildExecutor root = BuildExecutor.of(Path.of("target"));
        root.addSource("deps", Path.of("dependencies"));

        root.addModule("main-deps", (module, _) -> {
            module.addStep("properties", Bind.asDependencies("main.properties"), "../deps");
            module.addStep("resolved", new Resolve(repositories, resolvers), "properties");
            module.addStep("artifacts", new Download(repositories), "resolved");
        }, "deps");
        root.addModule("main", (module, _) -> {
            module.addSource("sources", Bind.asSources(), Path.of("sources"));
            module.addStep("classes", Javac.tool(), "sources", "../main-deps/artifacts");
            module.addStep("artifacts", Jar.tool(Jar.Sort.CLASSES), "classes");
        }, "main-deps");

        root.addModule("test-deps", (module, _) -> {
            module.addStep("properties", Bind.asDependencies("test.properties"), "../deps");
            module.addStep("resolved", new Resolve(repositories, resolvers), "properties");
            module.addStep("artifacts", new Download(repositories), "resolved");
        }, "deps");
        root.addModule("test", (module, _) -> {
            module.addSource("sources", Bind.asSources(), Path.of("tests"));
            module.addStep("classes", Javac.tool(), "sources", "../main/artifacts", "../test-deps/artifacts");
            module.addStep("artifacts", Jar.tool(Jar.Sort.CLASSES), "classes", "../test-deps/artifacts");
            module.addStep("tests", new Tests(TestEngine.JUNIT5), "classes", "artifacts", "../main/artifacts", "../test-deps/artifacts");
        }, "test-deps", "main");

        root.execute();
    }
}
