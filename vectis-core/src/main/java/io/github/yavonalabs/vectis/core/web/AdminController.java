package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.action.EntityAction;
import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLogService;
import io.github.yavonalabs.vectis.core.event.VectisChangeEvent;
import io.github.yavonalabs.vectis.core.metadata.AssociationDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.metadata.FieldDescriptor;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.query.PageResult;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import io.github.yavonalabs.vectis.core.widget.StatCard;
import io.github.yavonalabs.vectis.core.widget.StatCardProvider;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("${vectis.path:/admin}")
public class AdminController {

    private final EntityMetadataRegistry registry;
    private final DynamicCriteriaQueryEngine queryEngine;
    private final AdminPermissionEvaluator permissionEvaluator;
    private final EntityActionRegistry actionRegistry;
    private final List<StatCardProvider> statCardProviders;
    private final ApplicationEventPublisher eventPublisher;
    private final VectisAuditLogService auditLogService;
    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    @Value("${vectis.title:Operations Console}")
    private String adminTitle;

    public AdminController(
            EntityMetadataRegistry registry,
            DynamicCriteriaQueryEngine queryEngine,
            AdminPermissionEvaluator permissionEvaluator,
            EntityActionRegistry actionRegistry,
            ObjectProvider<List<StatCardProvider>> statCardProvidersProvider,
            ApplicationEventPublisher eventPublisher,
            VectisAuditLogService auditLogService
    ) {
        this.registry = registry;
        this.queryEngine = queryEngine;
        this.permissionEvaluator = permissionEvaluator;
        this.actionRegistry = actionRegistry;
        this.statCardProviders = statCardProvidersProvider.getIfAvailable(Collections::emptyList);
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model, Principal principal) {
        if (!permissionEvaluator.canAccessAdmin(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to Admin Console");
        }
        model.addAttribute("adminTitle", adminTitle);
        model.addAttribute("entities", registry.getAllDescriptors());
    }

    @GetMapping
    public String dashboard(Model model, Principal principal) {
        List<StatCard> stats = new ArrayList<>();
        for (StatCardProvider provider : statCardProviders) {
            stats.addAll(provider.getStatCards());
        }
        model.addAttribute("statCards", stats);
        model.addAttribute("recentAudits", auditLogService.findRecent(10));
        return "vectis/dashboard";
    }

    @GetMapping("/audit")
    public String auditLogView(Model model, Principal principal) {
        if (!permissionEvaluator.canViewAuditLogs(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to Audit Trail");
        }
        model.addAttribute("auditLogs", auditLogService.findRecent(100));
        return "vectis/audit";
    }

    @GetMapping("/{slug}")
    public String listView(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model,
            Principal principal,
            HttpServletRequest request
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canViewEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        PageResult<?> pageResult = queryEngine.findPage(descriptor, page, size, search, sort, dir);

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("pageResult", pageResult);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("size", size);
        model.addAttribute("actions", getAllowedActions(slug, descriptor, principal));

        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            return "vectis/fragments/table :: tableFragment";
        }

        return "vectis/list";
    }

    @GetMapping("/{slug}/view/{encodedId}")
    public String detailView(
            @PathVariable String slug,
            @PathVariable String encodedId,
            Model model,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canViewEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Object id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Map<String, List<Map<String, String>>> collectionDetails = new HashMap<>();
        Map<String, Map<String, String>> singleAssocDetails = new HashMap<>();

        for (AssociationDescriptor assoc : descriptor.associations()) {
            EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);

            if (assoc.isSingleValued()) {
                Object assocVal = wrapper.getPropertyValue(assoc.name());
                if (assocVal != null && targetDesc != null && targetDesc.idField() != null) {
                    BeanWrapper targetWrapper = PropertyAccessorFactory.forBeanPropertyAccess(assocVal);
                    Object targetId = targetWrapper.getPropertyValue(targetDesc.idField().name());
                    String targetEncodedId = IdCodec.encode(targetId, targetDesc.idField().isEmbeddedId());
                    singleAssocDetails.put(assoc.name(), Map.of(
                            "display", assocVal.toString(),
                            "slug", targetDesc.slug(),
                            "encodedId", targetEncodedId
                    ));
                }
            } else {
                try {
                    Object assocVal = wrapper.getPropertyValue(assoc.name());
                    if (assocVal instanceof Collection<?> col && targetDesc != null && targetDesc.idField() != null) {
                        List<Map<String, String>> items = new ArrayList<>();
                        for (Object item : col) {
                            BeanWrapper itemWrapper = PropertyAccessorFactory.forBeanPropertyAccess(item);
                            Object itemId = itemWrapper.getPropertyValue(targetDesc.idField().name());
                            String itemEncodedId = IdCodec.encode(itemId, targetDesc.idField().isEmbeddedId());
                            items.add(Map.of(
                                    "display", item.toString(),
                                    "slug", targetDesc.slug(),
                                    "encodedId", itemEncodedId
                            ));
                        }
                        collectionDetails.put(assoc.name(), items);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("entity", entity);
        model.addAttribute("encodedId", encodedId);
        model.addAttribute("singleAssocDetails", singleAssocDetails);
        model.addAttribute("collectionDetails", collectionDetails);
        model.addAttribute("actions", getAllowedActions(slug, descriptor, principal));
        model.addAttribute("entityAudits", auditLogService.findByEntity(slug, encodedId));

        return "vectis/detail";
    }

    @GetMapping("/{slug}/peek/{encodedId}")
    public String peekView(
            @PathVariable String slug,
            @PathVariable String encodedId,
            Model model,
            Principal principal
    ) {
        // Reuse the logic from detailView
        detailView(slug, encodedId, model, principal);
        model.addAttribute("isDrawer", true);
        // But only return the content fragment for the side drawer
        return "vectis/detail :: content";
    }

    @GetMapping("/{slug}/create")
    public String createForm(
            @PathVariable String slug,
            Model model,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        try {
            Object entity = descriptor.javaType().getDeclaredConstructor().newInstance();
            model.addAttribute("descriptor", descriptor);
            model.addAttribute("entity", entity);
            model.addAttribute("isNew", true);
            model.addAttribute("formOptions", loadFormAssociationOptions(descriptor));
            return "vectis/form";
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot instantiate entity: " + slug, e);
        }
    }

    @GetMapping("/{slug}/edit/{encodedId}")
    public String editForm(
            @PathVariable String slug,
            @PathVariable String encodedId,
            Model model,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Object id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("entity", entity);
        model.addAttribute("encodedId", encodedId);
        model.addAttribute("isNew", false);
        model.addAttribute("formOptions", loadFormAssociationOptions(descriptor));

        return "vectis/form";
    }

    @PostMapping("/{slug}/save")
    public String saveRecord(
            @PathVariable String slug,
            @RequestParam Map<String, String> formParams,
            RedirectAttributes redirectAttributes,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        String rawId = formParams.get("__id");
        boolean isNew = rawId == null || rawId.isBlank();

        try {
            Object entity;
            Map<String, Object> beforeSnapshot = new HashMap<>();

            if (isNew) {
                entity = descriptor.javaType().getDeclaredConstructor().newInstance();
            } else {
                Object id = IdCodec.decode(rawId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
                entity = queryEngine.findById(descriptor, id);
                if (entity == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + rawId);
                }
                beforeSnapshot = takeSnapshot(entity, descriptor);
            }

            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);

            for (FieldDescriptor field : descriptor.fields()) {
                if (field.isId() && !isNew) continue;
                if (field.isVersion()) {
                    String versionStr = formParams.get(field.name());
                    if (versionStr != null && !versionStr.isBlank()) {
                        wrapper.setPropertyValue(field.name(), conversionService.convert(versionStr, field.type()));
                    }
                    continue;
                }

                String paramVal = formParams.get(field.name());
                if (paramVal != null) {
                    if (field.isBoolean()) {
                        wrapper.setPropertyValue(field.name(), Boolean.parseBoolean(paramVal));
                    } else if (paramVal.isBlank()) {
                        wrapper.setPropertyValue(field.name(), null);
                    } else {
                        wrapper.setPropertyValue(field.name(), conversionService.convert(paramVal, field.type()));
                    }
                } else if (field.isBoolean()) {
                    wrapper.setPropertyValue(field.name(), false);
                }
            }

            for (AssociationDescriptor assoc : descriptor.associations()) {
                if (assoc.isSingleValued()) {
                    String assocId = formParams.get(assoc.name());
                    if (assocId != null && !assocId.isBlank()) {
                        EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                        if (targetDesc != null && targetDesc.idField() != null) {
                            Object decodedTargetId = IdCodec.decode(assocId, targetDesc.idField().type(), targetDesc.idField().isEmbeddedId());
                            Object targetEntity = queryEngine.findById(targetDesc, decodedTargetId);
                            wrapper.setPropertyValue(assoc.name(), targetEntity);
                        }
                    } else {
                        wrapper.setPropertyValue(assoc.name(), null);
                    }
                }
            }

            Object savedEntity;
            if (isNew) {
                queryEngine.persist(entity);
                savedEntity = entity;
            } else {
                savedEntity = queryEngine.save(entity);
            }

            Map<String, Object> afterSnapshot = takeSnapshot(savedEntity, descriptor);
            BeanWrapper savedWrapper = PropertyAccessorFactory.forBeanPropertyAccess(savedEntity);
            Object rawEntityId = descriptor.idField() != null ? savedWrapper.getPropertyValue(descriptor.idField().name()) : "N/A";
            String normalizedEncodedId = IdCodec.encode(rawEntityId, descriptor.idField() != null && descriptor.idField().isEmbeddedId());
            String username = principal != null ? principal.getName() : "system/ops";

            // PERSIST DURABLE AUDIT EVENT WITH REASON
            eventPublisher.publishEvent(new VectisChangeEvent(
                    this,
                    slug,
                    normalizedEncodedId,
                    isNew ? "Create Record" : "Update Record",
                    isNew ? VectisChangeEvent.OperationType.CREATE : VectisChangeEvent.OperationType.UPDATE,
                    beforeSnapshot,
                    afterSnapshot,
                    username,
                    formParams.getOrDefault("_reason", "Standard operational edit")
            ));

            redirectAttributes.addFlashAttribute("flashMessage",
                    "Record successfully " + (isNew ? "created" : "updated") + "!");
            return "redirect:/admin/" + slug;

        } catch (OptimisticLockException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Conflict: This record was modified by another user while you were editing it. Please refresh and try again.");
            return isNew ? "redirect:/admin/" + slug + "/create" : "redirect:/admin/" + slug + "/edit/" + rawId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save record: " + e.getMessage());
            return isNew ? "redirect:/admin/" + slug + "/create" : "redirect:/admin/" + slug + "/edit/" + rawId;
        }
    }

    @PostMapping("/{slug}/action/{actionId}/{encodedId}")
    @SuppressWarnings("unchecked")
    public String executeAction(
            @PathVariable String slug,
            @PathVariable String actionId,
            @PathVariable String encodedId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        Optional<? extends EntityAction<?>> actionOpt = actionRegistry.getAction(descriptor.javaType(), actionId);
        if (actionOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found: " + actionId);
        }

        EntityAction<Object> action = (EntityAction<Object>) actionOpt.get();

        if (action.getRequiredRole() != null && !action.getRequiredRole().isBlank()) {
            if (!permissionEvaluator.canExecuteAction(slug, actionId, principal)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Requires role " + action.getRequiredRole());
            }
        }

        Object id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        Map<String, Object> beforeSnapshot = takeSnapshot(entity, descriptor);

        try {
            if (action.getHandler() != null) {
                action.getHandler().accept(entity, allParams);
                queryEngine.save(entity);
            }

            Map<String, Object> afterSnapshot = takeSnapshot(entity, descriptor);
            String username = principal != null ? principal.getName() : "system/ops";

            // PERSIST DURABLE AUDIT EVENT WITH REASON
            eventPublisher.publishEvent(new VectisChangeEvent(
                    this,
                    slug,
                    encodedId,
                    action.getLabel(),
                    VectisChangeEvent.OperationType.ACTION,
                    beforeSnapshot,
                    afterSnapshot,
                    username,
                    allParams.getOrDefault("_reason", "Executed via Vectis Console")
            ));

            redirectAttributes.addFlashAttribute("flashMessage", "Action '" + action.getLabel() + "' executed successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Action failed: " + e.getMessage());
        }

        return "redirect:/admin/" + slug;
    }

    @PostMapping("/{slug}/delete/{encodedId}")
    public String deleteRecord(
            @PathVariable String slug,
            @PathVariable String encodedId,
            @RequestParam(required = false) Map<String, String> allParams,
            RedirectAttributes redirectAttributes,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canDeleteEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Object id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        Map<String, Object> beforeSnapshot = takeSnapshot(entity, descriptor);
        queryEngine.deleteById(descriptor, id);

        String username = principal != null ? principal.getName() : "system/ops";
        String reason = (allParams != null) ? allParams.getOrDefault("_reason", "Deleted via Vectis Console") : "Deleted via Vectis Console";

        // PERSIST DURABLE AUDIT EVENT WITH REASON
        eventPublisher.publishEvent(new VectisChangeEvent(
                this,
                slug,
                encodedId,
                "Delete Record",
                VectisChangeEvent.OperationType.DELETE,
                beforeSnapshot,
                Map.of(),
                username,
                reason
        ));

        redirectAttributes.addFlashAttribute("flashMessage", "Record #" + encodedId + " was successfully deleted.");
        return "redirect:/admin/" + slug;
    }

    private List<EntityAction<?>> getAllowedActions(String slug, EntityDescriptor descriptor, Principal principal) {
        return actionRegistry.getActionsForEntity(descriptor.javaType())
                .stream()
                .filter(action -> permissionEvaluator.canExecuteAction(slug, action.getId(), principal))
                .collect(Collectors.toList());
    }

    private EntityDescriptor getDescriptorOrThrow(String slug) {
        return registry.getBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found: " + slug));
    }

    private Map<String, Object> takeSnapshot(Object entity, EntityDescriptor descriptor) {
        Map<String, Object> snapshot = new HashMap<>();
        if (entity == null) return snapshot;
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (FieldDescriptor field : descriptor.fields()) {
            try {
                Object val = wrapper.getPropertyValue(field.name());
                snapshot.put(field.name(), val != null ? val.toString() : null);
            } catch (Exception ignored) {}
        }
        return snapshot;
    }

    private Map<String, List<Map<String, String>>> loadFormAssociationOptions(EntityDescriptor descriptor) {
        Map<String, List<Map<String, String>>> options = new HashMap<>();
        for (AssociationDescriptor assoc : descriptor.associations()) {
            if (assoc.isSingleValued()) {
                EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                if (targetDesc != null && targetDesc.idField() != null) {
                    List<?> targetEntities = queryEngine.findAll(targetDesc);
                    List<Map<String, String>> optionList = new ArrayList<>();
                    for (Object target : targetEntities) {
                        BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(target);
                        Object targetId = bw.getPropertyValue(targetDesc.idField().name());
                        String encodedTargetId = IdCodec.encode(targetId, targetDesc.idField().isEmbeddedId());
                        optionList.add(Map.of(
                                "id", encodedTargetId,
                                "label", target.toString()
                        ));
                    }
                    options.put(assoc.name(), optionList);
                }
            }
        }
        return options;
    }
}
