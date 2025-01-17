package build.buildbuddy.maven;

import build.buildbuddy.Repository;
import build.buildbuddy.RepositoryItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@FunctionalInterface
public interface MavenRepository extends Repository {

    @Override
    default MavenRepository prepend(Map<String, Path> coordinates) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                Path file = coordinates.get(groupId
                        + "/" + artifactId
                        + (type == null ? "/jar" : "/" + type)
                        + (classifier == null ? "" : "/" + classifier)
                        + "/" + version);
                if (file == null && type == null) {
                    file = coordinates.get(groupId
                            + "/" + artifactId
                            + (classifier == null ? "" : "/" + classifier)
                            + "/" + version);
                }
                return file != null
                        ? Optional.of(RepositoryItem.ofFile(file))
                        : MavenRepository.this.fetch(executor, groupId, artifactId, version, type, classifier, checksum);
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                // TODO: check prepended data for versions?
                return MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
            }
        };
    }

    default MavenRepository andThen(MavenRepository repository) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                Optional<RepositoryItem> candidate = MavenRepository.this.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        type,
                        classifier,
                        checksum);
                return candidate.isPresent() ? candidate : repository.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        type,
                        classifier,
                        checksum);
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                Optional<RepositoryItem> candidate = MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
                return candidate.isPresent() ? candidate : repository.fetchMetadata(executor, groupId, artifactId, checksum);
            }
        };
    }

    @Override
    default Optional<RepositoryItem> fetch(Executor executor, String coordinate) throws IOException {
        String[] elements = coordinate.split("/", 5);
        return switch (elements.length) {
            case 3 -> fetch(executor, elements[0], elements[1], elements[2], "jar", null, null);
            case 4 -> fetch(executor, elements[0], elements[1], elements[3], elements[2], null, null);
            case 5 -> fetch(executor, elements[0], elements[1], elements[4], elements[2], elements[3], null);
            default -> throw new IllegalArgumentException("Insufficient Maven coordinate: " + coordinate);
        };
    }

    Optional<RepositoryItem> fetch(Executor executor,
                                   String groupId,
                                   String artifactId,
                                   String version,
                                   String type,
                                   String classifier,
                                   String checksum) throws IOException;


    default Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                   String groupId,
                                                   String artifactId,
                                                   String checksum) throws IOException {
        return Optional.empty();
    }
}
