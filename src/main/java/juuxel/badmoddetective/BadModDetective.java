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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class BadModDetective implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        BadModException badMod = new BadModException();

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
        }

        badMod.throwIfNeeded();

        LOGGER.info("[BadModDetective] No bad mods found!");
    }

    private static final class BadModException extends RuntimeException {
        private final Multimap<ModContainer, String> errors = MultimapBuilder.hashKeys().arrayListValues().build();

        BadModException() {
        }

        void addError(ModContainer mod, String error) {
            errors.put(mod, error);
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
                    .sorted(Comparator.comparing(it -> it.getMetadata().getId()))
                    .map(it -> {
                        ModMetadata meta = it.getMetadata();
                        String id = meta.getId();
                        String name = meta.getName();
                        String authors = meta.getAuthors().isEmpty() ? "unknown" :
                                meta.getAuthors()
                                        .stream()
                                        .map(Person::getName)
                                        .collect(Collectors.joining(", "));

                        return "- " + id + " (" + name + ") by " + authors + "\n" +
                                errors.get(it).stream()
                                        .map(error -> "  - " + error)
                                        .collect(Collectors.joining("\n"));
                    })
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
