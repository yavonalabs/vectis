package io.github.yavonalabs.vectis.core.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import java.util.*;

/** Historical snapshots are projected through today's exposure metadata before rendering. */
public final class AuditPresentation {
    private AuditPresentation() {}
    public static List<String> changes(VectisAuditLog audit, EntityDescriptor descriptor) {
        try {
            var mapper = new ObjectMapper();
            JsonNode before = parse(mapper, audit.getBeforeSnapshotJson());
            JsonNode after = parse(mapper, audit.getAfterSnapshotJson());
            var changes = new ArrayList<String>();
            for (var field : descriptor.fields()) {
                if (field.isId() || field.isVersion()) continue;
                JsonNode left = before.get(field.name()), right = after.get(field.name());
                if (!Objects.equals(left, right)) changes.add(field.displayName() + ": " + display(left) + " → " + display(right));
            }
            return changes;
        } catch (Exception ex) { return List.of("Change details unavailable."); }
    }
    private static JsonNode parse(ObjectMapper mapper, String json) throws Exception {
        if (json == null) return mapper.createObjectNode();
        if (json.length() > 1048576) throw new IllegalArgumentException("Snapshot too large");
        JsonNode value = mapper.readTree(json);
        if (!value.isObject()) throw new IllegalArgumentException("Invalid snapshot");
        return value;
    }
    private static String display(JsonNode value) {
        if (value == null || value.isNull()) return "Not provided";
        if (!value.isValueNode()) return "Details unavailable";
        String text = value.asText();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }
}
