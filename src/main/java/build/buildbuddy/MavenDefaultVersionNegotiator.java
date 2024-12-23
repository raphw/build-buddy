package build.buildbuddy;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Supplier;

import static build.buildbuddy.MavenPomResolver.missing;
import static build.buildbuddy.MavenPomResolver.toChildren;

public class MavenDefaultVersionNegotiator implements MavenVersionNegotiator {

    private final MavenRepository repository;
    private final DocumentBuilderFactory factory;
    private final Map<MavenPomResolver.DependencyName, Metadata> cache = new HashMap<>();

    private MavenDefaultVersionNegotiator(MavenRepository repository, DocumentBuilderFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    public static Supplier<MavenVersionNegotiator> mavenRules(MavenRepository repository) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return () -> new MavenDefaultVersionNegotiator(repository, factory);
    }

    @Override
    public String resolve(String groupId,
                          String artifactId,
                          String type,
                          String classifier,
                          String version) throws IOException {
        return switch (version) {
            case "RELEASE" -> toMetadata(groupId, artifactId).release();
            case "LATEST" -> toMetadata(groupId, artifactId).latest();
            case String range when (range.startsWith("[") || range.startsWith("(")) && (range.endsWith("]") || range.endsWith(")")) -> {
                String value = range.substring(1, range.length() - 2), minimum, maximum;
                int includeMinimum = range.startsWith("[") ? 0 : 1, includeMaximum = range.endsWith("]") ? 0 : 1, index = value.indexOf(',');
                if (index == -1) {
                    minimum = maximum = value.trim();
                } else {
                    minimum = value.substring(0, index).trim();
                    maximum = value.substring(index + 1).trim();
                }
                yield toMetadata(groupId, artifactId).versions().stream()
                        .filter(candidate -> compare(candidate, minimum) < includeMinimum)
                        .filter(candidate -> compare(candidate, maximum) < includeMaximum)
                        .reduce((left, right) -> right)
                        .orElseThrow(() -> new IllegalStateException("Could not resolve version in range: " + version));
            }
            default -> version;
        };
    }

    @Override
    public String resolve(String groupId,
                          String artifactId,
                          String type,
                          String classifier,
                          String version,
                          SequencedSet<String> versions) throws IOException {
        return versions.getFirst(); // TODO
    }

    private Metadata toMetadata(String groupId, String artifactId) throws IOException {
        Metadata metadata = cache.get(new MavenPomResolver.DependencyName(groupId, artifactId));
        if (metadata == null) {
            Document document;
            try (InputStream inputStream = repository.fetchMetadata(groupId, artifactId, null).toInputStream()) {
                document = factory.newDocumentBuilder().parse(inputStream);
            } catch (SAXException | ParserConfigurationException e) {
                throw new IllegalStateException(e);
            }
            metadata = switch (document.getDocumentElement().getAttribute("modelVersion")) {
                case "1.1.0" -> {
                    Node versioning = toChildren(document.getDocumentElement())
                            .filter(node -> Objects.equals(node.getLocalName(), "versioning"))
                            .findFirst()
                            .orElseThrow(missing("versioning"));
                    yield new Metadata(
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getLocalName(), "latest"))
                                    .findFirst()
                                    .map(Node::getTextContent)
                                    .orElseThrow(missing("latest")),
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getLocalName(), "release"))
                                    .findFirst()
                                    .map(Node::getTextContent)
                                    .orElseThrow(missing("release")),
                            toChildren(versioning)
                                    .filter(node -> Objects.equals(node.getLocalName(), "versions"))
                                    .findFirst()
                                    .stream()
                                    .flatMap(MavenPomResolver::toChildren)
                                    .filter(node -> Objects.equals(node.getLocalName(), "version"))
                                    .map(Node::getTextContent)
                                    .toList());
                }
                case null, default -> throw new IllegalStateException("Unknown model version: " +
                        document.getDocumentElement().getAttribute("modelVersion"));
            };
            cache.put(new MavenPomResolver.DependencyName(groupId, artifactId), metadata);
        }
        return metadata;
    }

    static int compare(String left, String right) {
        int leftIndex = 0, rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftNext = leftIndex, rightNext = rightIndex;
            while (leftNext < left.length()) {
                if (left.charAt(leftNext) == '.' || left.charAt(leftNext) == '-') {
                    break;
                } else {
                    leftNext++;
                }
            }
            while (rightNext < right.length()) {
                if (right.charAt(rightNext) == '.' || right.charAt(rightNext) == '-') {
                    break;
                } else {
                    rightNext++;
                }
            }
            boolean leftText = false, rightText = false;
            int leftInteger = 0, rightInteger = 0;
            try {
                leftInteger = Integer.parseInt(left, leftIndex, leftNext, 10);
            } catch (NumberFormatException ignored) {
                leftText = true;
            }
            try {
                rightInteger = Integer.parseInt(right, rightIndex, rightNext, 10);
            } catch (NumberFormatException ignored) {
                rightText = true;
            }
            int comparison;
            if (leftText && rightText) {
                comparison = CharSequence.compare(
                        left.subSequence(leftIndex, leftNext),
                        right.substring(rightIndex, rightNext));
            } else if (leftText) {
                return 1;
            } else if (rightText) {
                return -1;
            } else {
                comparison = Integer.compare(leftInteger, rightInteger);
            }
            if (comparison != 0) {
                return comparison;
            }
            leftIndex = leftNext;
            rightIndex = rightNext;
        }
        if (leftIndex < left.length()) {
            return -1;
        } else if (rightIndex < right.length()) {
            return 1;
        } else {
            return 0;
        }
    }

    private record Metadata(String latest, String release, List<String> versions) {
    }
}
