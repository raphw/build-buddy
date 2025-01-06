package build.buildbuddy.module;

import build.buildbuddy.Identification;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

public class ModularJarResolver {

    public Identification identify(Path jar) throws IOException {
        ModuleDescriptor descriptor = ModuleFinder.of(jar).findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Does not contain a module: " + jar))
                .descriptor();
        if (descriptor.isAutomatic()) {
            throw new IllegalArgumentException("Cannot resolve dependencies from automatic module: " + jar);
        }
        SequencedMap<String, String> dependencies = new LinkedHashMap<>();
        descriptor.requires().stream()
                .filter(requires -> !requires.name().equals("java.base"))
                .filter(requires -> !requires.accessFlags().contains(AccessFlag.STATIC_PHASE))
                .forEach(require -> dependencies.put(require.name(), ""));
        return new Identification(descriptor.name(), dependencies);
    }
}
