package io.github.yavonalabs.vectis.core.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class IdCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdCodec() {}

    public static String encode(Object id, boolean isEmbedded) {
        if (id == null) return "";
        if (!isEmbedded) {
            return String.valueOf(id);
        }
        try {
            String json = MAPPER.writeValueAsString(id);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode embedded id: " + id, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T decode(String rawId, Class<T> targetClass, boolean isEmbedded) {
        if (rawId == null || rawId.isBlank()) return null;
        if (!isEmbedded) {
            return parseScalar(rawId, targetClass);
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(rawId);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, targetClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode embedded id: " + rawId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseScalar(String rawId, Class<T> targetClass) {
        if (Long.class.equals(targetClass) || long.class.equals(targetClass)) {
            return (T) Long.valueOf(rawId);
        }
        if (Integer.class.equals(targetClass) || int.class.equals(targetClass)) {
            return (T) Integer.valueOf(rawId);
        }
        if (String.class.equals(targetClass)) {
            return (T) rawId;
        }
        if (java.util.UUID.class.equals(targetClass)) {
            return (T) java.util.UUID.fromString(rawId);
        }
        throw new UnsupportedOperationException("Unsupported scalar ID type: " + targetClass.getName());
    }
}