package chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ChatPacketParser {

    private ChatPacketParser() {
    }

    public static Map<String, String> parse(String packet) {
        try {
            JsonElement element = JsonParser.parseString(packet);
            if (!element.isJsonObject()) {
                throw new JsonParseException("Packet must be a JSON object.");
            }

            JsonObject object = element.getAsJsonObject();
            Map<String, String> fields = new LinkedHashMap<String, String>();
            Set<Map.Entry<String, JsonElement>> entries = object.entrySet();
            for (Map.Entry<String, JsonElement> entry : entries) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    fields.put(entry.getKey(), value.getAsString());
                }
            }
            return fields;
        } catch (JsonParseException | IllegalStateException exception) {
            System.err.println("Ignoring malformed chat packet: "
                    + exception.getMessage());
            return Collections.emptyMap();
        }
    }
}
