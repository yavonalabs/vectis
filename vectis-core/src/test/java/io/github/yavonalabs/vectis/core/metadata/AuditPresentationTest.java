package io.github.yavonalabs.vectis.core.metadata;

import io.github.yavonalabs.vectis.core.audit.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AuditPresentationTest {
    @Test void previouslyStoredHiddenFieldsAreNeverProjected() {
        var field = new FieldDescriptor("name", "Name", String.class, false, false, false, true, true, Set.of(), null, null, "");
        var descriptor = new EntityDescriptor("Entry", "entry", "Entries", Object.class, null, null, List.of(field), List.of());
        var audit = new VectisAuditLog();
        audit.setBeforeSnapshotJson("{\"name\":\"Before\",\"secret\":\"old secret\"}");
        audit.setAfterSnapshotJson("{\"name\":\"After\",\"secret\":\"new secret\"}");
        assertThat(AuditPresentation.changes(audit, descriptor)).containsExactly("Name: Before → After");
    }
}
