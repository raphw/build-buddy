package build.buildbuddy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HashFunctionTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void can_write_file_and_read_file() throws IOException {
        Path file = temporaryFolder.newFile().toPath();
        HashFunction.write(file, Map.of(Path.of("foo"), new byte[]{1, 2, 3}));
        Map<Path, byte[]> checksums = HashFunction.read(file);
        assertThat(checksums).containsOnlyKeys(Path.of("foo"));
        assertThat(checksums.get(Path.of("foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_folder() throws IOException {
        Path folder = temporaryFolder.newFolder().toPath();
        try (Writer writer = Files.newBufferedWriter(folder.resolve("foo"))) {
            writer.append("bar");
        }
        Map<Path, byte[]> checksums = HashFunction.read(folder, file -> new byte[]{1, 2, 3});
        assertThat(checksums).containsOnlyKeys(Path.of("foo"));
        assertThat(checksums.get(Path.of("foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_nested_folder() throws IOException {
        Path folder = temporaryFolder.newFolder().toPath();
        try (Writer writer = Files.newBufferedWriter(Files.createDirectory(folder.resolve("bar")).resolve("foo"))) {
            writer.append("bar");
        }
        Map<Path, byte[]> checksums = HashFunction.read(folder, file -> new byte[]{1, 2, 3});
        assertThat(checksums).containsOnlyKeys(Path.of("bar/foo"));
        assertThat(checksums.get(Path.of("bar/foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_empty_folder() throws IOException {
        Path folder = temporaryFolder.newFolder().toPath();
        Map<Path, byte[]> checksums = HashFunction.read(folder, file -> {
            throw new UnsupportedOperationException();
        });
        assertThat(checksums).isEmpty();
    }
}
