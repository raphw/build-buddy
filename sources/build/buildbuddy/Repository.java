package build.buildbuddy;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@FunctionalInterface
public interface Repository {

    Optional<RepositoryItem> fetch(Executor executor, String coordinate) throws IOException;

    default Repository andThen(Repository repository) {
        return (executor, coordinate) -> {
            Optional<RepositoryItem> candidate = fetch(executor, coordinate);
            return candidate.isPresent() ? candidate : repository.fetch(executor, coordinate);
        };
    }

    default Repository cached(Path folder) {
        ConcurrentMap<String, Path> cache = new ConcurrentHashMap<>();
        return (executor, coordinate) -> {
            Path previous = cache.get(coordinate);
            if (previous != null) {
                return Optional.of(RepositoryItem.ofFile(previous));
            }
            RepositoryItem item = Repository.this.fetch(executor, coordinate).orElse(null);
            if (item == null) {
                return Optional.empty();
            } else {
                Path file = item.getFile().orElse(null), target = folder.resolve(URLEncoder.encode(
                        coordinate,
                        StandardCharsets.UTF_8));
                if (file != null) {
                    Files.createLink(target, file);
                } else {
                    try (InputStream inputStream = item.toInputStream()) {
                        Files.copy(inputStream, target);
                    }
                }
                Path concurrent = cache.putIfAbsent(coordinate, target);
                return Optional.of(RepositoryItem.ofFile(concurrent == null ? target : concurrent));
            }
        };
    }

    default Repository prepend(Map<String, Path> coordinates) {
        return (executor, coordinate) -> {
            Path file = coordinates.get(coordinate);
            return file == null ? fetch(executor, coordinate) : Optional.of(RepositoryItem.ofFile(file));
        };
    }

    static Repository ofUris(Map<String, URI> uris) {
        return (_, coordinate) -> {
            URI uri = uris.get(coordinate);
            return uri == null ? Optional.empty() : Optional.of(() -> uri.toURL().openStream());
        };
    }

    static Repository ofFiles(Map<String, Path> files) {
        return (_, coordinate) -> {
            Path file = files.get(coordinate);
            return file == null ? Optional.empty() : Optional.of(RepositoryItem.ofFile(file));
        };
    }

    static Repository empty() {
        return (_, _) -> Optional.empty();
    }

    static Repository files() {
        return (_, coordinate) -> {
            Path file = Paths.get(coordinate);
            return Files.exists(file) ? Optional.of(RepositoryItem.ofFile(file)) : Optional.empty();
        };
    }

    static Map<String, Repository> ofCoordinates(Iterable<Path> folders) throws IOException {
        Map<String, Map<String, Path>> artifacts = new HashMap<>();
        for (Path folder : folders) {
            Path file = folder.resolve(BuildStep.COORDINATES);
            if (Files.exists(file)) {
                Properties properties = new SequencedProperties();
                try (Reader reader = Files.newBufferedReader(file)) {
                    properties.load(reader);
                }
                for (String coordinate : properties.stringPropertyNames()) {
                    String location = properties.getProperty(coordinate);
                    if (location.isEmpty()) {
                        throw new IllegalStateException("Unresolved location for " + coordinate);
                    }
                    int index = coordinate.indexOf('/');
                    artifacts.computeIfAbsent(
                            coordinate.substring(0, index),
                            _ -> new HashMap<>()).put(coordinate.substring(index + 1), file);
                }
            }
        }
        return artifacts.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> ofFiles(entry.getValue())));
    }
}
