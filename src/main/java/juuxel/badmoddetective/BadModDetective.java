package juuxel.badmoddetective;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.metadata.ModMetadataV0;
import net.fabricmc.loader.metadata.ModMetadataV1;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.AbstractLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BadModDetective implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        BadModException badMod = new BadModException();
        String conTaterName = FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_1703");
        Class<?> rawConTater = null;

        try {
            rawConTater = Class.forName(conTaterName);
        } catch (Throwable t) {
            LOGGER.warn("Could not find Menu", t);
        }

        Class<?> conTater = rawConTater; // effectively final for lambda

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = mod.getMetadata();
            String version = meta.getVersion().getFriendlyString();
            if (version.equals("$version") || version.equals("${version}")) {
                badMod.addError(mod, "Missing version replacement: " + meta.getVersion().getFriendlyString());
            }
            if (meta instanceof ModMetadataV0) {
                badMod.addError(mod, "Outdated schema: v0");
            } else if (meta instanceof ModMetadataV1) {
                ((ModMetadataV1) meta).emitFormatWarnings(new BadModExceptionLogger(badMod, mod));
            }
            Path buildRefmap = mod.getPath("build-refmap.json");
            if (Files.exists(buildRefmap)) {
                badMod.addError(mod, "Found unnamed mixin refmap 'build-refmap.json'");
            }

            if (conTater != null) {
                try {
                    Path root = mod.getRootPath();
                    Files.walk(root)
                            .filter(it -> it.toString().endsWith("Container.class") && !it.getFileName().toString().startsWith("Mixin"))
                            .map(root::relativize)
                            .forEach(it -> {
                                String rawName = it.toString().replace(it.getFileSystem().getSeparator(), ".");
                                String name = rawName.substring(0, rawName.length() - ".class".length());

                                try {
                                    Class<?> clazz = Class.forName(name);

                                    if (conTater.isAssignableFrom(clazz)) {
                                        badMod.addError(mod, "Menu is called con tater: " + name);
                                    }
                                } catch (Throwable ignored) {
                                }
                            });
                } catch (IOException e) {
                    LOGGER.warn("Could not walk mod file tree of mod '{}'", meta.getId(), e);
                }
            }
        }

        badMod.throwIfNeeded();

        LOGGER.info("[BadModDetective] No bad mods found!");
    }

    private static <T> Comparator<T> merge(Comparator<T> primary, Comparator<T> secondary) {
        return (a, b) -> {
            int primaryResult = primary.compare(a, b);
            if (primaryResult != 0) return primaryResult;
            return secondary.compare(a, b);
        };
    }

    private enum SourceType {
        MOD, PACKAGE
    }

    private abstract static class Source {
        final SourceType type;

        protected Source(SourceType type) {
            this.type = type;
        }

        public final SourceType getType() {
            return type;
        }

        public abstract String getId();

        public abstract String getInfo();

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Source && getId().equals(((Source) obj).getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId());
        }
    }

    private static final class ModSource extends Source {
        private final ModContainer mod;

        ModSource(ModContainer mod) {
            super(SourceType.MOD);
            this.mod = mod;
        }

        @Override
        public String getId() {
            return mod.getMetadata().getId();
        }

        @Override
        public String getInfo() {
            ModMetadata meta = mod.getMetadata();
            String id = meta.getId();
            String name = meta.getName();
            String authors = meta.getAuthors().isEmpty() ? "unknown" :
                    meta.getAuthors()
                            .stream()
                            .map(Person::getName)
                            .collect(Collectors.joining(", "));

            return id + " (" + name + ") by " + authors;
        }
    }

    private static final class PackageSource extends Source {
        private final String pkg;

        PackageSource(String className) {
            super(SourceType.PACKAGE);

            String[] components = className.split("\\.");
            if (components.length <= 1) {
                pkg = "";
            } else {
                pkg = String.join(".", Arrays.copyOf(components, Math.min(3, components.length - 1)));
            }
        }

        @Override
        public String getId() {
            return pkg;
        }

        @Override
        public String getInfo() {
            return "Package " + pkg;
        }
    }

    private static final class BadModException extends RuntimeException {
        private final Multimap<Source, String> errors = MultimapBuilder.hashKeys().arrayListValues().build();

        BadModException() {
        }

        void addError(ModContainer mod, String error) {
            errors.put(new ModSource(mod), error);
        }

        void addError(Class<?> source, String error) {
            errors.put(new PackageSource(source.getName()), error);
        }

        void throwIfNeeded() throws BadModException {
            if (!errors.isEmpty()) {
                throw this;
            }
        }

        @Override
        public String getMessage() {
            return "Bad mods found: \n" + buildTree();
        }

        private String buildTree() {
            return errors.keySet().stream()
                    .sorted(merge(Comparator.comparing(Source::getType), Comparator.comparing(Source::getId)))
                    .map(it -> "- " + it.getInfo() + "\n" +
                            errors.get(it).stream()
                                    .map(error -> "  - " + error)
                                    .collect(Collectors.joining("\n")))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static final class BadModExceptionLogger extends AbstractLogger {
        private final BadModException badMod;
        private final ModContainer mod;

        BadModExceptionLogger(BadModException badMod, ModContainer mod) {
            this.badMod = badMod;
            this.mod = mod;
        }

        @Override
        public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable t) {
            badMod.addError(mod, message.getFormattedMessage());
        }

        @Override
        public Level getLevel() {
            return Level.INFO;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, Message message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, CharSequence message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, Object message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object... params) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
            return true;
        }
    }
}
