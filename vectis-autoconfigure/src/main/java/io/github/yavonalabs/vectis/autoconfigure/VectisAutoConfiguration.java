package io.github.yavonalabs.vectis.autoconfigure;

import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLog;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLogService;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import io.github.yavonalabs.vectis.core.security.AllowAllPermissionEvaluator;
import io.github.yavonalabs.vectis.core.web.AdminController;
import io.github.yavonalabs.vectis.core.widget.StatCardProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.HashSet;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({EntityManagerFactory.class, EntityManager.class})
@ConditionalOnProperty(prefix = "vectis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(VectisProperties.class)
@EntityScan(basePackageClasses = {VectisAuditLog.class})
public class VectisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntityMetadataRegistry entityMetadataRegistry(
            EntityManagerFactory entityManagerFactory,
            VectisProperties properties
    ) {
        return new EntityMetadataRegistry(entityManagerFactory, new HashSet<>(properties.getAllowedEntities()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicCriteriaQueryEngine dynamicCriteriaQueryEngine(EntityManager entityManager, jakarta.validation.Validator validator) {
        return new DynamicCriteriaQueryEngine(entityManager, validator);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnMissingBean
    public AdminPermissionEvaluator springSecurityPermissionEvaluator(
            EntityMetadataRegistry metadataRegistry,
            EntityActionRegistry actionRegistry) {
        return new io.github.yavonalabs.vectis.core.security.SpringSecurityPermissionEvaluator(metadataRegistry, actionRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminPermissionEvaluator defaultPermissionEvaluator() {
        return new AllowAllPermissionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public VectisAuditLogService vectisAuditLogService(EntityManager entityManager) {
        return new VectisAuditLogService(entityManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EntityActionRegistry entityActionRegistry(
            ObjectProvider<List<io.github.yavonalabs.vectis.core.action.EntityActionContributor<?>>> contributors,
            ApplicationContext applicationContext
    ) {
        return new EntityActionRegistry(contributors, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminController adminController(
            EntityMetadataRegistry registry,
            DynamicCriteriaQueryEngine queryEngine,
            AdminPermissionEvaluator permissionEvaluator,
            EntityActionRegistry actionRegistry,
            ObjectProvider<List<StatCardProvider>> statCardProviders,
            ApplicationEventPublisher eventPublisher,
            VectisAuditLogService auditLogService
    ) {
        return new AdminController(
                registry,
                queryEngine,
                permissionEvaluator,
                actionRegistry,
                statCardProviders,
                eventPublisher,
                auditLogService
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.web.ActionPreviewController actionPreviewController(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            EntityActionRegistry actionRegistry,
            EntityMetadataRegistry descriptorRegistry,
            DynamicCriteriaQueryEngine queryEngine,
            EntityManager entityManager
    ) {
        return new io.github.yavonalabs.vectis.core.web.ActionPreviewController(
                objectMapper,
                actionRegistry,
                descriptorRegistry,
                queryEngine,
                entityManager
        );
    }
}
