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
    private final io.github.yavonalabs.vectis.core.mutation.ActionMutationService actionMutations;
    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    @Value("${vectis.title:Operations Console}")
    private String adminTitle;
    @Value("${vectis.path:/admin}") private String adminPath;
    @Value("${vectis.environment:Not specified}") private String environmentLabel;
    @Value("${vectis.logout-path:}") private String logoutPath;

    public AdminController(
            EntityMetadataRegistry registry,
            DynamicCriteriaQueryEngine queryEngine,
            AdminPermissionEvaluator permissionEvaluator,
            EntityActionRegistry actionRegistry,
            ObjectProvider<List<StatCardProvider>> statCardProvidersProvider,
            ApplicationEventPublisher eventPublisher,
            VectisAuditLogService auditLogService,
            io.github.yavonalabs.vectis.core.mutation.ActionMutationService actionMutations
    ) {
        this.registry = registry;
        this.queryEngine = queryEngine;
        this.permissionEvaluator = permissionEvaluator;
        this.actionRegistry = actionRegistry;
        this.statCardProviders = statCardProvidersProvider.getIfAvailable(Collections::emptyList);
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.actionMutations = actionMutations;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model, Principal principal, HttpServletRequest request) {
        if (!permissionEvaluator.canAccessAdmin(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to Admin Console");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String navigationSlug = pathVariables == null ? null : pathVariables.get("slug");
        String listQuery = ListNavigation.sanitize(request.getParameter("_list"));
        if (navigationSlug != null) {
            String listPath = adminPath + "/" + navigationSlug;
            if (request.getRequestURI().equals(request.getContextPath() + listPath))
                listQuery = ListNavigation.sanitize(request.getQueryString());
            model.addAttribute("listUrl", listPath + ListNavigation.querySuffix(listQuery));
        }
        model.addAttribute("listQuery", listQuery.isEmpty() ? null : listQuery);
        model.addAttribute("adminTitle", adminTitle);
        model.addAttribute("adminPath", adminPath);
        model.addAttribute("environmentLabel", environmentLabel);
        model.addAttribute("logoutPath", logoutPath.isBlank() ? null : logoutPath);
        model.addAttribute("operatorName", principal == null ? "Unknown" : principal.getName());
        model.addAttribute("canViewAudit", permissionEvaluator.canViewAuditLogs(principal));
        model.addAttribute("fieldErrors", Map.of());
        model.addAttribute("submittedValues", Map.of());
        model.addAttribute("entities", registry.getAllDescriptors().stream()
                .filter(d -> permissionEvaluator.canViewEntity(d.slug(), principal)).toList());
    }

    @GetMapping
    public String dashboard(Model model, Principal principal) {
        List<StatCard> stats = new ArrayList<>();
        for (StatCardProvider provider : statCardProviders) {
            stats.addAll(provider.getStatCards());
        }
        model.addAttribute("statCards", stats);
        model.addAttribute("recentAudits", permissionEvaluator.canViewAuditLogs(principal)
                ? auditLogService.findRecent(10).stream().filter(a -> permissionEvaluator.canViewEntity(a.getEntitySlug(), principal)).toList() : List.of());
        return "vectis/dashboard";
    }

    @GetMapping("/audit")
    public String auditLogView(Model model, Principal principal) {
        if (!permissionEvaluator.canViewAuditLogs(principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to Audit Trail");
        }
        model.addAttribute("auditLogs", auditLogService.findRecent(100).stream().filter(a -> permissionEvaluator.canViewEntity(a.getEntitySlug(), principal)).toList());
        return "vectis/audit";
    }

    @GetMapping("/{slug}")
    public String listView(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam org.springframework.util.MultiValueMap<String, String> parameters,
            Model model,
            Principal principal,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canViewEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        List<String> filterFields = parameters.getOrDefault("filterField", List.of());
        List<String> filterOperators = parameters.getOrDefault("filterOp", List.of());
        List<String> filterValues = parameters.getOrDefault("filterValue", List.of());
        PageResult<?> pageResult;
        try {
            if (search != null && search.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is limited to 200 characters.");
            var filters = io.github.yavonalabs.vectis.core.query.RecordFilter.parse(descriptor, filterFields, filterOperators, filterValues);
            pageResult = queryEngine.findPage(descriptor, page, size, search, sort, dir, filters);
            model.addAttribute("filterCount", filters.size());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.BAD_REQUEST) throw ex;
            response.setStatus(400);
            response.setHeader("X-Vectis-Filter-Error", "true");
            model.addAttribute("filterError", ex.getReason());
            model.addAttribute("filterCount", 0);
            pageResult = new PageResult<>(List.of(), 0, Math.max(1, size), 0, 0);
        }
        model.addAttribute("filterFields", filterFields);
        model.addAttribute("filterOperators", filterOperators);
        model.addAttribute("filterValues", filterValues);
        model.addAttribute("filterableFields", descriptor.fields().stream().filter(io.github.yavonalabs.vectis.core.query.RecordFilter::supported).toList());
        List<Map<String, String>> filterRows = new ArrayList<>();
        for (int i = 0; i < 3; i++) filterRows.add(Map.of("field", i < filterFields.size() ? filterFields.get(i) : "",
                "operator", i < filterOperators.size() ? filterOperators.get(i) : "eq", "value", i < filterValues.size() ? filterValues.get(i) : ""));
        model.addAttribute("filterRows", filterRows);

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("pageResult", pageResult);
        Map<Object, Map<String, String>> associationLabels = new HashMap<>();
        Map<Object, String> recordLabels = new HashMap<>();
        for (Object row : pageResult.content()) {
            recordLabels.put(row, io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(descriptor, row));
            Map<String, String> labels = new HashMap<>();
            BeanWrapper rowWrapper = PropertyAccessorFactory.forBeanPropertyAccess(row);
            for (AssociationDescriptor assoc : descriptor.associations()) {
                if (!assoc.isSingleValued()) continue;
                var target = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                if (target == null || !permissionEvaluator.canViewEntity(target.slug(), principal)) continue;
                Object value = rowWrapper.getPropertyValue(assoc.name());
                if (value != null) labels.put(assoc.name(), io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(target, value));
            }
            associationLabels.put(row, labels);
        }
        model.addAttribute("associationLabels", associationLabels);
        model.addAttribute("recordLabels", recordLabels);
        model.addAttribute("canEdit", permissionEvaluator.canEditEntity(slug, principal));
        model.addAttribute("canDelete", permissionEvaluator.canDeleteEntity(slug, principal));
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

        Object id = decodeId(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id, false);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Map<String, List<Map<String, String>>> collectionDetails = new HashMap<>();
        Map<String, Map<String, String>> singleAssocDetails = new HashMap<>();

        for (AssociationDescriptor assoc : descriptor.associations()) {
            EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);
            if (targetDesc == null || !permissionEvaluator.canViewEntity(targetDesc.slug(), principal)) continue;

            if (assoc.isSingleValued()) {
                Object assocVal = wrapper.getPropertyValue(assoc.name());
                if (assocVal != null && targetDesc != null && targetDesc.idField() != null) {
                    BeanWrapper targetWrapper = PropertyAccessorFactory.forBeanPropertyAccess(assocVal);
                    Object targetId = targetWrapper.getPropertyValue(targetDesc.idField().name());
                    String targetEncodedId = IdCodec.encode(targetId, targetDesc.idField().isEmbeddedId());
                    singleAssocDetails.put(assoc.name(), Map.of(
                            "display", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(targetDesc, assocVal),
                            "slug", targetDesc.slug(),
                            "encodedId", targetEncodedId
                    ));
                }
            } else {
                collectionDetails.put(assoc.name(), queryEngine.findRelatedPage(descriptor, id, assoc, targetDesc, 0).content());
            }
        }

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("entity", entity);
        model.addAttribute("encodedId", encodedId);
        model.addAttribute("singleAssocDetails", singleAssocDetails);
        model.addAttribute("collectionDetails", collectionDetails);
        model.addAttribute("actions", getAllowedActions(slug, descriptor, principal));
        model.addAttribute("entityAudits", permissionEvaluator.canViewAuditLogs(principal) ? auditLogService.findByEntity(slug, encodedId) : List.of());
        model.addAttribute("recordLabel", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(descriptor, entity));
        model.addAttribute("canEdit", permissionEvaluator.canEditEntity(slug, principal));
        model.addAttribute("canDelete", permissionEvaluator.canDeleteEntity(slug, principal));

        return "vectis/detail";
    }

    @GetMapping("/{slug}/view/{encodedId}/related/{association}")
    public String relatedRecords(@PathVariable String slug, @PathVariable String encodedId,
            @PathVariable String association, @RequestParam(defaultValue = "0") int page,
            Model model, Principal principal) {
        EntityDescriptor source = getDescriptorOrThrow(slug);
        if (!permissionEvaluator.canViewEntity(slug, principal))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        AssociationDescriptor assoc = source.associations().stream()
                .filter(a -> a.name().equals(association) && !a.isSingleValued()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship not found"));
        EntityDescriptor target = registry.getByClass(assoc.targetEntityClass())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relationship unavailable"));
        if (!permissionEvaluator.canViewEntity(target.slug(), principal))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        Object id = decodeId(encodedId, source.idField().type(), source.idField().isEmbeddedId());
        Object entity = queryEngine.findById(source, id, false);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        model.addAttribute("descriptor", source);
        model.addAttribute("encodedId", encodedId);
        model.addAttribute("association", assoc);
        model.addAttribute("recordLabel", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(source, entity));
        model.addAttribute("relatedPage", queryEngine.findRelatedPage(source, id, assoc, target, page));
        return "vectis/related";
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

        if (!permissionEvaluator.canViewEntity(slug, principal) || !permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        try {
            Object entity = descriptor.javaType().getDeclaredConstructor().newInstance();
            model.addAttribute("descriptor", descriptor);
            model.addAttribute("entity", entity);
            model.addAttribute("isNew", true);
            model.addAttribute("formOptions", loadFormAssociationOptions(descriptor, principal, entity));
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

        if (!permissionEvaluator.canViewEntity(slug, principal) || !permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Object id = decodeId(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        model.addAttribute("descriptor", descriptor);
        model.addAttribute("entity", entity);
        model.addAttribute("encodedId", encodedId);
        model.addAttribute("isNew", false);
        model.addAttribute("recordLabel", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(descriptor, entity));
        model.addAttribute("formOptions", loadFormAssociationOptions(descriptor, principal, entity));

        return "vectis/form";
    }

    @PostMapping("/{slug}/save")
    public String saveRecord(
            @PathVariable String slug,
            @RequestParam Map<String, String> formParams,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Principal principal
    ) {
        EntityDescriptor descriptor = getDescriptorOrThrow(slug);

        if (!permissionEvaluator.canViewEntity(slug, principal) || !permissionEvaluator.canEditEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        requireReason(formParams);
        String rawId = formParams.get("__id");
        boolean isNew = rawId == null || rawId.isBlank();
        Object entity = null;

        try {
            Map<String, Object> beforeSnapshot = new HashMap<>();

            if (isNew) {
                entity = descriptor.javaType().getDeclaredConstructor().newInstance();
            } else {
                Object id = decodeId(rawId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
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
                    EntityDescriptor target = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                    if (target == null || !permissionEvaluator.canViewEntity(target.slug(), principal)) {
                        if (formParams.containsKey(assoc.name())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change this relationship.");
                        }
                        continue;
                    }
                    String assocId = formParams.get(assoc.name());
                    if (assocId != null && !assocId.isBlank()) {
                        EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                        if (targetDesc != null && targetDesc.idField() != null) {
                            if (!permissionEvaluator.canViewEntity(targetDesc.slug(), principal)) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to related record.");
                            }
                            Object decodedTargetId = decodeId(assocId, targetDesc.idField().type(), targetDesc.idField().isEmbeddedId());
                            Object targetEntity = queryEngine.findById(targetDesc, decodedTargetId);
                            if (targetEntity == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related record does not exist.");
                            wrapper.setPropertyValue(assoc.name(), targetEntity);
                        }
                    } else if (assocId != null) {
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
            if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Redirect", request.getContextPath() + adminPath + "/" + slug + "/view/" + normalizedEncodedId + ListNavigation.contextSuffix(request.getParameter("_list")));
                // A 200 response is required: browsers consume headers on a 302 internally.
                org.springframework.web.servlet.support.RequestContextUtils.getOutputFlashMap(request)
                        .put("flashMessage", "Record successfully " + (isNew ? "created" : "updated") + "!");
                org.springframework.web.servlet.support.RequestContextUtils.saveOutputFlashMap(
                        request.getContextPath() + adminPath + "/" + slug + "/view/" + normalizedEncodedId + ListNavigation.contextSuffix(request.getParameter("_list")), request, response);
                return null;
            }
            return "redirect:" + adminPath + "/" + slug + ListNavigation.querySuffix(request.getParameter("_list"));

        } catch (ResponseStatusException e) { throw e;
        } catch (OptimisticLockException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Conflict: This record was modified by another user while you were editing it. Please refresh and try again.");
            return (isNew ? "redirect:" + adminPath + "/" + slug + "/create" : "redirect:" + adminPath + "/" + slug + "/edit/" + rawId) + ListNavigation.contextSuffix(request.getParameter("_list"));
        } catch (jakarta.validation.ConstraintViolationException e) {
            model.addAttribute("descriptor", descriptor);
            model.addAttribute("entity", entity);
            model.addAttribute("isNew", isNew);
            model.addAttribute("encodedId", isNew ? null : rawId);
            model.addAttribute("formOptions", loadFormAssociationOptions(descriptor, principal, entity));
            
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            e.getConstraintViolations().forEach(violation -> {
                String fieldName = violation.getPropertyPath().toString();
                if (descriptor.fields().stream().anyMatch(f -> f.name().equals(fieldName))) {
                    fieldErrors.merge(fieldName, violation.getMessage(), (a, b) -> a + "; " + b);
                }
            });
            model.addAttribute("errorMessage", "Please correct the fields below before saving.");
            model.addAttribute("fieldErrors", fieldErrors);
            model.addAttribute("submittedValues", formParams);
            model.addAttribute("recordLabel", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(descriptor, entity));
            model.addAttribute("changeReason", formParams.get("_reason"));
            return "true".equalsIgnoreCase(request.getHeader("HX-Request"))
                    ? "vectis/fragments/edit-form :: editFormFragment" : "vectis/form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "The record could not be saved. Check its values and try again.");
            return (isNew ? "redirect:" + adminPath + "/" + slug + "/create" : "redirect:" + adminPath + "/" + slug + "/edit/" + rawId) + ListNavigation.contextSuffix(request.getParameter("_list"));
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

        try {
            var result = actionMutations.execute(slug, actionId, encodedId, allParams);
            redirectAttributes.addFlashAttribute("flashMessage", "Action '" + result.actionLabel() + "' executed successfully!");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (jakarta.validation.ConstraintViolationException e) {
            StringBuilder sb = new StringBuilder("Action validation failed:\n");
            e.getConstraintViolations().forEach(violation -> {
                String fieldName = violation.getPropertyPath().toString();
                if (descriptor.fields().stream().anyMatch(f -> f.name().equals(fieldName))) {
                    sb.append(fieldName).append(": ").append(violation.getMessage()).append("\n");
                }
            });
            redirectAttributes.addFlashAttribute("errorMessage", sb.toString());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "The action's outcome could not be confirmed. Check the record and any connected systems before attempting it again; an external effect may already have occurred.");
        }

        return "redirect:" + adminPath + "/" + slug;
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

        if (!permissionEvaluator.canViewEntity(slug, principal) || !permissionEvaluator.canDeleteEntity(slug, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Object id = decodeId(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        requireReason(allParams);
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
        return "redirect:" + adminPath + "/" + slug;
    }

    private Object decodeId(String value, Class<?> type, boolean embedded) {
        try { return IdCodec.decode(value, type, embedded); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record identifier."); }
    }

    private void requireReason(Map<String, String> params) {
        String reason = params == null ? null : params.get("_reason");
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a reason for this change (1–1000 characters).");
        }
    }

    private List<EntityAction<?>> getAllowedActions(String slug, EntityDescriptor descriptor, Principal principal) {
        return actionRegistry.getActionsForEntity(descriptor.javaType())
                .stream()
                .filter(action -> permissionEvaluator.canExecuteAction(slug, action.getId(), principal))
                .sorted(Comparator.comparing((EntityAction<?> action) -> action.getRiskLevel().ordinal()).thenComparing(EntityAction::getLabel))
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

    @GetMapping("/{slug}/relationships/{association}/options")
    @ResponseBody
    public Map<String, Object> relationshipOptions(@PathVariable String slug, @PathVariable String association,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            Principal principal) {
        EntityDescriptor source = getDescriptorOrThrow(slug);
        if (!permissionEvaluator.canViewEntity(slug, principal) || !permissionEvaluator.canEditEntity(slug, principal))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot edit this relationship.");
        var assoc = source.associations().stream().filter(a -> a.name().equals(association) && a.isSingleValued())
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var target = registry.getByClass(assoc.targetEntityClass()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!permissionEvaluator.canViewEntity(target.slug(), principal)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (search.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is limited to 200 characters.");
        var results = queryEngine.findPage(target, page, 25, search, null, "asc");
        return Map.of("items", results.content().stream().map(item -> relationshipOption(target, item)).toList(),
                "page", page, "hasNext", results.hasNext());
    }

    private Map<String, String> relationshipOption(EntityDescriptor target, Object entity) {
        var wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        return Map.of("id", IdCodec.encode(wrapper.getPropertyValue(target.idField().name()), target.idField().isEmbeddedId()),
                "label", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(target, entity));
    }

    private Map<String, List<Map<String, String>>> loadFormAssociationOptions(EntityDescriptor descriptor, Principal principal, Object entity) {
        Map<String, List<Map<String, String>>> options = new HashMap<>();
        var sourceWrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (AssociationDescriptor assoc : descriptor.associations()) {
            if (!assoc.isSingleValued()) continue;
            EntityDescriptor target = registry.getByClass(assoc.targetEntityClass()).orElse(null);
            if (target == null || target.idField() == null || !permissionEvaluator.canViewEntity(target.slug(), principal)) continue;
            List<Map<String, String>> choices = new ArrayList<>(queryEngine.findPage(target, 0, 25, null, null, "asc")
                    .content().stream().map(item -> relationshipOption(target, item)).toList());
            Object current = sourceWrapper.getPropertyValue(assoc.name());
            if (current != null) {
                Map<String, String> selected = relationshipOption(target, current);
                if (choices.stream().noneMatch(option -> option.get("id").equals(selected.get("id")))) choices.add(selected);
                // Render selection from the target's ID metadata, not the source entity's ID name.
                choices = choices.stream().map(option -> {
                    Map<String, String> result = new HashMap<>(option);
                    result.put("selected", String.valueOf(option.get("id").equals(selected.get("id"))));
                    return result;
                }).toList();
            }
            options.put(assoc.name(), choices);
        }
        return options;
    }
}
