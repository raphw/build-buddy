package build.buildbuddy;

import module java.base;

public record BuildStepContext(Path previous, Path next, Path supplement) {
}
