package build.buildbuddy.project;

import build.buildbuddy.BuildExecutor;
import build.buildbuddy.BuildExecutorModule;
import build.buildbuddy.SequencedProperties;
import build.buildbuddy.step.Group;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MultiProjectModule implements BuildExecutorModule {

    public static final String IDENTIFY = "identify", GROUP = "group", BUILD = "build", MODULE = "module";

    private final Pattern QUALIFIER = Pattern.compile("../identify/module/([a-zA-Z0-9-]+)(?:/[a-zA-Z0-9-]+)?");

    private final BuildExecutorModule identifier;
    private final Function<String, Optional<String>> resolver;
    private final Function<SequencedMap<String, SequencedSet<String>>, MultiProject> factory;

    public MultiProjectModule(BuildExecutorModule identifier,
                              Function<String, Optional<String>> resolver,
                              Function<SequencedMap<String, SequencedSet<String>>, MultiProject> factory) {
        this.identifier = identifier;
        this.resolver = resolver;
        this.factory = factory;
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addModule(IDENTIFY, identifier, resolver);
        buildExecutor.addModule(BUILD, (process, identified) -> {
            SequencedMap<String, String> modules = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> identifiers = new LinkedHashMap<>();
            for (String identifier : identified.keySet()) {
                Matcher matcher = QUALIFIER.matcher(identifier);
                if (matcher.matches()) {
                    String name = matcher.group(1);
                    modules.put(identifier, name);
                    identifiers.computeIfAbsent(name, _ -> new LinkedHashSet<>()).add(identifier);
                }
            }
            process.addStep(GROUP,
                    new Group(identifier -> Optional.of(modules.get(identifier))),
                    modules.sequencedKeySet());
            process.addModule(MODULE, (build, paths) -> {
                SequencedMap<String, SequencedSet<String>> pending = new LinkedHashMap<>();
                Path groups = paths.get(PREVIOUS + GROUP).resolve(Group.GROUPS);
                for (Map.Entry<String, SequencedSet<String>> entry : identifiers.entrySet()) {
                    Properties properties = new SequencedProperties();
                    try (Reader reader = Files.newBufferedReader(groups.resolve(entry.getKey() + ".properties"))) {
                        properties.load(reader);
                    }
                    pending.put(entry.getKey(), new LinkedHashSet<>(properties.stringPropertyNames()));
                }
                MultiProject project = factory.apply(pending);
                while (!pending.isEmpty()) {
                    Iterator<Map.Entry<String, SequencedSet<String>>> it = pending.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, SequencedSet<String>> entry = it.next();
                        if (Collections.disjoint(entry.getValue(), pending.keySet())) {
                            SequencedMap<String, Path> arguments = new LinkedHashMap<>();
                            identifiers.get(entry.getKey()).forEach(identifier -> arguments.put(
                                    PREVIOUS + identifier,
                                    paths.get(PREVIOUS + identifier)));
                            build.addModule(entry.getKey(),
                                    project.module(entry.getKey(), entry.getValue(), arguments),
                                    entry.getValue());
                            it.remove();
                        }
                    }
                }
            }, Stream.concat(Stream.of(GROUP), identified.sequencedKeySet().stream()).toArray(String[]::new));
        }, IDENTIFY);
    }
}
