package fr.fidorial.registrygen.generate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class BlockLightPropertiesReportParser {

    private static final Gson GSON = new Gson();

    private BlockLightPropertiesReportParser() {
        throw new UnsupportedOperationException();
    }

    static Map<String, Entry> read(final Path lightDataFile) throws IOException {
        try (final Reader reader = Files.newBufferedReader(lightDataFile, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<Map<String, Entry>>() {}.getType());
        }
    }

    record Entry(int[] opacity, int[] emission) {
    }
}
