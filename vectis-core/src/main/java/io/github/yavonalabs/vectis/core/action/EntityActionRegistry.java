package io.github.yavonalabs.vectis.core.action;

import io.github.yavonalabs.vectis.core.annotation.AdminAction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EntityActionRegistry {

    private final Map<Class<?>, List<EntityAction<?>>> actionsByClass = new ConcurrentHashMap<>();
    private final ObjectProvider<List<EntityActionContributor<?>>> contributorsProvider;
    private final ApplicationContext applicationContext;

    public EntityActionRegistry(
            ObjectProvider<List<EntityActionContributor<?>>> contributorsProvider,
            ApplicationContext applicationContext
    ) {
        this.contributorsProvider = contributorsProvider;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<EntityActionContributor<?>> contributors = contributorsProvider.getIfAvailable();
        if (contributors != null) {
            for (EntityActionContributor<?> contributor : contributors) {
                actionsByClass.computeIfAbsent(contributor.getEntityClass(), k -> new ArrayList<>())
                        .addAll(contributor.getActions());
            }
        }

        scanSpringBeansForActions();
    }

    @SuppressWarnings("unchecked")
    public <T> List<EntityAction<T>> getActionsForEntity(Class<T> entityClass) {
        List<EntityAction<?>> existing = actionsByClass.computeIfAbsent(entityClass, k -> new ArrayList<>());
        List<EntityAction<?>> entityMethods = scanEntityMethods(entityClass);

        List<EntityAction<T>> combined = new ArrayList<>();
        Set<String> actionIds = new HashSet<>();

        for (EntityAction<?> a : existing) {
            if (actionIds.add(a.getId())) {
                combined.add((EntityAction<T>) a);
            }
        }
        for (EntityAction<?> a : entityMethods) {
            if (actionIds.add(a.getId())) {
                combined.add((EntityAction<T>) a);
            }
        }

        return Collections.unmodifiableList(combined);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<EntityAction<T>> getAction(Class<T> entityClass, String actionId) {
        return getActionsForEntity(entityClass).stream()
                .filter(a -> a.getId().equals(actionId))
                .findFirst();
    }

    private void scanSpringBeansForActions() {
        Map<String, Object> serviceBeans = new HashMap<>();
        serviceBeans.putAll(applicationContext.getBeansWithAnnotation(Service.class));
        serviceBeans.putAll(applicationContext.getBeansWithAnnotation(Component.class));

        for (Object bean : serviceBeans.values()) {
            if (bean == this) continue;
            Class<?> beanClass = bean.getClass();

            for (Method method : beanClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(AdminAction.class)) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length >= 1) {
                        Class<?> targetEntityClass = paramTypes[0];
                        AdminAction ann = method.getAnnotation(AdminAction.class);
                        String id = method.getName();
                        String label = !ann.label().isBlank() ? ann.label() : splitCamelCase(method.getName());
                        String preAuth = extractPreAuthorize(method);

                        EntityAction<Object> action = EntityAction.make(id)
                                .label(label)
                                .color(ann.color())
                                .icon(ann.icon())
                                .requiresRole(ann.requiredRole())
                                .requiresPreAuthorize(preAuth)
                                .modalTitle(label)
                                .modalDescription(ann.confirmMessage())
                                .risk(ann.risk())
                                .requiresConfirmation(ann.requiresConfirmation())
                                .handler((entity, params) -> {
                                    try {
                                        method.setAccessible(true);
                                        if (method.getParameterCount() == 1) {
                                            method.invoke(bean, entity);
                                        } else if (method.getParameterCount() == 2 && method.getParameterTypes()[1].equals(Map.class)) {
                                            method.invoke(bean, entity, params);
                                        } else {
                                            throw new IllegalArgumentException("Unsupported @AdminAction method parameters on Spring bean: " + method.getName());
                                        }
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to invoke service @AdminAction " + id + ": " + e.getMessage(), e);
                                    }
                                });

                        actionsByClass.computeIfAbsent(targetEntityClass, k -> new ArrayList<>()).add(action);
                    }
                }
            }
        }
    }

    private List<EntityAction<?>> scanEntityMethods(Class<?> clazz) {
        List<EntityAction<?>> discovered = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AdminAction.class)) {
                AdminAction ann = method.getAnnotation(AdminAction.class);
                String id = method.getName();
                String label = !ann.label().isBlank() ? ann.label() : splitCamelCase(method.getName());
                String preAuth = extractPreAuthorize(method);

                EntityAction<Object> action = EntityAction.make(id)
                        .label(label)
                        .color(ann.color())
                        .icon(ann.icon())
                        .requiresRole(ann.requiredRole())
                        .requiresPreAuthorize(preAuth)
                        .modalTitle(label)
                        .modalDescription(ann.confirmMessage())
                        .risk(ann.risk())
                        .requiresConfirmation(ann.requiresConfirmation())
                        .handler((entity, params) -> {
                            try {
                                method.setAccessible(true);
                                if (method.getParameterCount() == 0) {
                                    method.invoke(entity);
                                } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(Map.class)) {
                                    method.invoke(entity, params);
                                } else {
                                    throw new IllegalArgumentException("Unsupported @AdminAction method signature: " + method.getName());
                                }
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to invoke entity @AdminAction " + id + ": " + e.getMessage(), e);
                            }
                        });

                discovered.add(action);
            }
        }
        return discovered;
    }

    private String extractPreAuthorize(Method method) {
        for (java.lang.annotation.Annotation a : method.getAnnotations()) {
            if (a.annotationType().getName().equals("org.springframework.security.access.prepost.PreAuthorize")) {
                try {
                    Method valueMethod = a.annotationType().getMethod("value");
                    return (String) valueMethod.invoke(a);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }

    private String splitCamelCase(String s) {
        return s.replaceAll(String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
        ), " ").trim();
    }
}