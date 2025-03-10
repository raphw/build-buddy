package build.buildbuddy.maven;

public record MavenDependencyName(String groupId, String artifactId) {

    public static final MavenDependencyName EXCLUDE_ALL = new MavenDependencyName("*", "*");
}
