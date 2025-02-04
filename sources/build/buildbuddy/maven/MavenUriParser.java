package build.buildbuddy.maven;

import build.buildbuddy.SequencedProperties;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class MavenUriParser implements Function<String, String> {

    private final Function<String, String> hosts;

    public MavenUriParser() {
        hosts = _ -> "maven";
    }

    public MavenUriParser(Function<String, String> hosts) {
        this.hosts = hosts;
    }

    public static Function<String, Optional<String>> ofUris(MavenUriParser parser,
                                                            String location,
                                                            Iterable<Path> folders) throws IOException {
        Properties properties = new SequencedProperties();
        for (Path folder : folders) {
            Path file = folder.resolve(location);
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    properties.load(reader);
                }
            }
        }
        return property -> Optional.ofNullable(properties.getProperty(property)).map(parser);
    }

    @Override
    public String apply(String value) {
        URI uri = URI.create(value);
        String[] elements = uri.getPath().split("/");
        String type = elements[elements.length - 1].substring(elements[elements.length - 1].lastIndexOf('.') + 1);
        String classifier = elements[elements.length - 1].substring(
                elements[elements.length - 3].length(),
                elements[elements.length - 1].length() - type.length() - elements[elements.length - 2].length() - 2);
        return requireNonNull(hosts.apply(uri.getHost()), "Unknown host: " + uri.getHost())
                + "/" + String.join(".", Arrays.asList(elements).subList(2, elements.length - 3))
                + "/" + elements[elements.length - 3]
                + (Objects.equals(type, "jar") && classifier.isEmpty() ? "" : "/" + type)
                + (classifier.isEmpty() ? "" : "/" + classifier.substring(1))
                + "/" + elements[elements.length - 2];
    }
}
