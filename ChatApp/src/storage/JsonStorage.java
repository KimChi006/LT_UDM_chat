package storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Common file layer for all JSON data of the application.
 *
 * This class only reads/writes files and converts text to Gson trees.
 * It does not know anything about messages, users or groups, so the
 * concrete Storage classes can reuse it.
 */
public class JsonStorage {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final File file;

    /**
     * Resolves the data file from the folder where the application is started.
     * Running from ChatApp uses ChatApp/data; running from the repository root
     * uses ChatApp/data as well. No machine-specific absolute path is used.
     */
    public JsonStorage(String fileName) {
        this(resolveDataFile(fileName));
    }

    /** Used by tests that need to write into a temporary folder. */
    public JsonStorage(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        this.file = file;
    }

    public static File resolveDataFile(String fileName) {
        File dataFolder = new File("data");
        if (dataFolder.isDirectory()) {
            return new File(dataFolder, fileName);
        }

        File chatAppData = new File("ChatApp", "data");
        if (chatAppData.isDirectory()) {
            return new File(chatAppData, fileName);
        }

        // Fallback: let writeText() create the folder later.
        return new File("data", fileName);
    }

    public File getFile() {
        return file;
    }

    public boolean exists() {
        return file.isFile();
    }

    /**
     * Returns an empty string for a missing file, an empty file or a read error.
     * Callers must handle that case instead of crashing.
     */
    public String readText() {
        if (!exists() || file.length() == 0) {
            return "";
        }

        try {
            return new String(
                    Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            System.err.println("Cannot read " + file + ": " + exception.getMessage());
            return "";
        }
    }

    /**
     * Writes through a temporary file first. If the process stops in the
     * middle, the old data file stays intact.
     */
    public boolean writeText(String text) {
        String content = text == null ? "" : text;

        Path target = file.toPath().toAbsolutePath();
        Path parent = target.getParent();
        Path temporary = parent.resolve(file.getName() + ".tmp");

        try {
            Files.createDirectories(parent);
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            System.err.println("Cannot write " + file + ": " + exception.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Nothing else can be done; the error was already reported.
            }
            return false;
        }
    }

    /**
     * @return empty array for a missing/empty file, null when JSON is invalid.
     *         null tells callers that they must not overwrite this file.
     */
    public JsonArray readArray() {
        String text = readText();
        if (text.trim().isEmpty()) {
            return new JsonArray();
        }

        try {
            JsonElement element = JsonParser.parseString(text);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray();
            }
            System.err.println("Expected a JSON array in " + file);
        } catch (JsonParseException exception) {
            System.err.println("Invalid JSON in " + file + ": " + exception.getMessage());
        }
        return null;
    }

    /**
     * @return empty object for a missing/empty file, null when JSON is invalid.
     */
    public JsonObject readObject() {
        String text = readText();
        if (text.trim().isEmpty()) {
            return new JsonObject();
        }

        try {
            JsonElement element = JsonParser.parseString(text);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            System.err.println("Expected a JSON object in " + file);
        } catch (JsonParseException exception) {
            System.err.println("Invalid JSON in " + file + ": " + exception.getMessage());
        }
        return null;
    }

    public boolean writeArray(JsonArray array) {
        return writeText(GSON.toJson(array == null ? new JsonArray() : array));
    }

    public boolean writeObject(JsonObject object) {
        return writeText(GSON.toJson(object == null ? new JsonObject() : object));
    }
}
