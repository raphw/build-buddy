package build.buildbuddy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface Repository {

    InputStreamSource fetch(String coordinate) throws IOException;

    @FunctionalInterface
    interface InputStreamSource {

        default Optional<Path> getPath() {
            return Optional.empty();
        }

        InputStream toInputStream() throws IOException;
    }
}
