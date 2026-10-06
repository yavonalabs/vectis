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
            EntityActionRegistry actionRegistry, VectisProperties properties) {
        return new io.github.yavonalabs.vectis.core.security.SpringSecurityPermissionEvaluator(metadataRegistry, actionRegistry, properties.getRoles(), properties.getReadOnlyRoles());
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminPermissionEvaluator defaultPermissionEvaluator() {
        return new io.github.yavonalabs.vectis.core.security.DenyAllPermissionEvaluator();
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
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    public io.github.yavonalabs.vectis.core.mutation.MutationActorProvider springMutationActorProvider() {
        return new io.github.yavonalabs.vectis.core.mutation.SpringSecurityMutationActorProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.mutation.ActionMutationService actionMutationService(
            EntityMetadataRegistry metadata, EntityActionRegistry actions, DynamicCriteriaQueryEngine queries,
            AdminPermissionEvaluator permissions, ApplicationEventPublisher events,
            ObjectProvider<io.github.yavonalabs.vectis.core.mutation.MutationActorProvider> actors) {
        return new io.github.yavonalabs.vectis.core.mutation.ActionMutationService(metadata, actions, queries,
                permissions, actors.getIfAvailable(() -> () -> null), events);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.mutation.RecordMutationService recordMutationService(
            EntityMetadataRegistry metadata, DynamicCriteriaQueryEngine queries,
            AdminPermissionEvaluator permissions, ApplicationEventPublisher events,
            ObjectProvider<io.github.yavonalabs.vectis.core.mutation.MutationActorProvider> actors) {
        return new io.github.yavonalabs.vectis.core.mutation.RecordMutationService(metadata, queries,
                permissions, actors.getIfAvailable(() -> () -> null), events);
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
            VectisAuditLogService auditLogService,
            io.github.yavonalabs.vectis.core.mutation.ActionMutationService actionMutations,
            io.github.yavonalabs.vectis.core.mutation.RecordMutationService recordMutations
    ) {
        return new AdminController(
                registry,
                queryEngine,
                permissionEvaluator,
                actionRegistry,
                statCardProviders,
                eventPublisher,
                auditLogService,
                actionMutations,
                recordMutations
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.web.ActionPreviewController actionPreviewController(
            EntityActionRegistry actions, EntityMetadataRegistry registry, DynamicCriteriaQueryEngine queries,
            AdminPermissionEvaluator permissions, jakarta.validation.Validator validator) {
        return new io.github.yavonalabs.vectis.core.web.ActionPreviewController(actions, registry, queries, permissions, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.web.GlobalSearchController globalSearchController(
            EntityMetadataRegistry registry, EntityManager entityManager, AdminPermissionEvaluator permissions) {
        return new io.github.yavonalabs.vectis.core.web.GlobalSearchController(registry, entityManager, permissions);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.web.VectisExceptionHandler vectisExceptionHandler() {
        return new io.github.yavonalabs.vectis.core.web.VectisExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.github.yavonalabs.vectis.core.context.DryRunProtectionAspect dryRunProtectionAspect() {
        return new io.github.yavonalabs.vectis.core.context.DryRunProtectionAspect();
    }
}
