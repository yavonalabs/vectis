package io.github.yavonalabs.vectis.core.export;

import io.github.yavonalabs.vectis.core.metadata.*;
import io.github.yavonalabs.vectis.core.mutation.MutationActorProvider;
import io.github.yavonalabs.vectis.core.query.*;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import io.github.yavonalabs.vectis.core.view.SavedViewState;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RecordExportService {
    private final EntityMetadataRegistry metadata;
    private final DynamicCriteriaQueryEngine queries;
    private final AdminPermissionEvaluator permissions;
    private final MutationActorProvider actors;
    public RecordExportService(EntityMetadataRegistry metadata, DynamicCriteriaQueryEngine queries,
            AdminPermissionEvaluator permissions, MutationActorProvider actors) {
        this.metadata = metadata; this.queries = queries; this.permissions = permissions; this.actors = actors;
    }
    public byte[] export(String slug, Map<String, List<String>> input) {
        var actor = actors.currentActor();
        if (actor == null || !permissions.canAccessAdmin(actor) || !permissions.canViewEntity(slug, actor) || !permissions.canExportEntity(slug, actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Export is not available for your account.");
        var descriptor = metadata.getBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (input == null || input.size() > 16) throw invalid();
        SavedViewState.capture(descriptor, input); // validates the same search, filter and sort contract as saved views
        List<String> names = input.getOrDefault("column", List.of());
        if (names.isEmpty() || names.size() > 20 || new HashSet<>(names).size() != names.size()) throw invalid();
        List<FieldDescriptor> columns = names.stream().map(name -> descriptor.fields().stream()
                .filter(f -> f.name().equals(name) && !f.isVersion() && !f.isEmbeddedId()
                        && (f.isString() || f.isNumeric() || f.isBoolean() || f.isEnum() || f.isDateOrTime()))
                .findFirst().orElseThrow(RecordExportService::invalid)).toList();
        var filters = RecordFilter.parse(descriptor, input.getOrDefault("filterField", List.of()),
                input.getOrDefault("filterOp", List.of()), input.getOrDefault("filterValue", List.of()));
        var rows = queries.exportRows(descriptor, columns, first(input, "search"), first(input, "sort"), first(input, "dir"), filters);
        if (rows.size() > 1000) throw limit("More than 1,000 records match. Narrow the filters before exporting.");
        var output = new java.io.ByteArrayOutputStream();
        append(output, columns.stream().map(FieldDescriptor::displayName).toList());
        for (var row : rows) {
            if (Thread.currentThread().isInterrupted()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Export cancelled.");
            List<String> values = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                Object value = row.get(i);
                String text = value == null ? "" : value instanceof Enum<?> e ? e.name() : value.toString();
                if (text.length() > 2048) throw limit("A selected value exceeds 2,048 characters. Remove that column before exporting.");
                values.add(text);
            }
            append(output, values);
        }
        return output.toByteArray();
    }
    private static String first(Map<String, List<String>> input, String name) { return input.getOrDefault(name, List.of("")).get(0); }
    private static void append(java.io.ByteArrayOutputStream output, List<String> cells) {
        String line = cells.stream().map(RecordExportService::cell).collect(java.util.stream.Collectors.joining(",")) + "\r\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (output.size() + bytes.length > 2 * 1024 * 1024) throw limit("Export exceeds 2 MiB. Narrow the filters or select fewer columns.");
        output.writeBytes(bytes);
    }
    public static String cell(String value) {
        String leading = value.stripLeading();
        if (!leading.isEmpty() && ("=+-@".indexOf(leading.charAt(0)) >= 0 || value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n')) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose 1–20 available scalar columns and valid applied filters."); }
    private static ResponseStatusException limit(String reason) { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, reason); }
}
