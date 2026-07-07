package com.videocharter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.videocharter.model.AppState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public class StateStore {

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Object lock = new Object();
    private AppState state;

    public StateStore(Path file) {
        this.file = file;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.state = load();
    }

    public <T> T read(Function<AppState, T> reader) {
        synchronized (lock) {
            return reader.apply(state);
        }
    }

    public <T> T mutate(Function<AppState, T> writer) {
        synchronized (lock) {
            T result = writer.apply(state);
            save();
            return result;
        }
    }

    private AppState load() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (Files.notExists(file)) {
                AppState freshState = new AppState();
                freshState.normalize();
                return freshState;
            }
            AppState loaded = objectMapper.readValue(file.toFile(), AppState.class);
            loaded.normalize();
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load state from " + file, exception);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            objectMapper.writeValue(file.toFile(), state);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save state to " + file, exception);
        }
    }
}
