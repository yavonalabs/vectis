package io.github.yavonalabs.vectis.core.metadata;

import io.github.yavonalabs.vectis.core.annotation.AdminEntity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class EntityMetadataRegistryTest {

    @AdminEntity(label = "Custom Opted In")
    static class OptedInEntity {
        private Long id;
        private String name;
    }

    static class IgnoredEntity {
        private Long id;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testStrictOptInScanning() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        Metamodel metamodel = Mockito.mock(Metamodel.class);
        when(emf.getMetamodel()).thenReturn(metamodel);

        EntityType optedInType = Mockito.mock(EntityType.class);
        when(optedInType.getName()).thenReturn("OptedInEntity");
        when(optedInType.getJavaType()).thenReturn(OptedInEntity.class);
        when(optedInType.getAttributes()).thenReturn(Set.of());

        EntityType ignoredType = Mockito.mock(EntityType.class);
        when(ignoredType.getName()).thenReturn("IgnoredEntity");
        when(ignoredType.getJavaType()).thenReturn(IgnoredEntity.class);
        when(ignoredType.getAttributes()).thenReturn(Set.of());

        when(metamodel.getEntities()).thenReturn(Set.of(optedInType, ignoredType));

        EntityMetadataRegistry registry = new EntityMetadataRegistry(emf);

        assertTrue(registry.getByClass(OptedInEntity.class).isPresent(), "Opted-in entity should be registered");
        assertFalse(registry.getByClass(IgnoredEntity.class).isPresent(), "Non-opted-in entity should NOT be registered");
    }
}