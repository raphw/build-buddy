Build Buddy
===========

POC for a simple-enough, yet powerful enough build tool that targets Java, and is written and configured in Java, and 
that has inherent support for (a) parallel incremental builds, and therewith build immutability and (b) seamless 
validation of downloads from external (untrusted) sources by checksum validation.

As a goal, the build tool should be stored in source alongside a project without any external installation. A build 
should be executable by using Java alone, by embracing the JVMs ability to run programs from source files. This avoids
storing precompiled binaries in repositories, and allows for the execution of builds in environments that only have the
JVM installed without the deployment of build tool wrappers that often entail a (cachable) download of the tool. It
should be possible to manage updates of these sources easily, and to add extensions (plugins) to the base implementation
alongside.

The build tool should only rely on the Java standard library and should be launchable using a command such as:

    java build.Main

where Main is a class located in the project's build folder. This should be employed for this project, too, so it can
document the use of this tool within its own source.

The POC is currently missing:
- Task to download dependencies (starting with Maven Central) based on a simple dependency descriptor file.
- Task to validate downloaded dependencies against a well-known checksum.
- Task to create a POM file from dependencies.
- Task to create META-INF.
- Builder class for a BuildExecutor that offers convenient defaults.
- Configurable support for reacting to incremental build information. (if changed, if not changed, always, custom rules)
- Extend BuildResult class to carry custom context information.
- Task for javadoc tool.
- Task for source jars.
- Task for JUnit (4, keep it simple).
- Task to add GPG signatures of artifacts.
- Task to publish to Maven Central.
- Refactor this project to use itself as build tool. (Retain POM for IDE support.)
- Extend all build step implementations to support their standard options.