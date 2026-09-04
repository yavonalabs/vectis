package io.github.yavonalabs.vectis.core.action;

import java.util.Map;
import java.util.function.BiConsumer;

public class EntityAction<T> {

    private final String id;
    private String label;
    private String color = "indigo"; // indigo, emerald, rose, amber, slate
    private String icon = "lightning";
    private String requiredRole;
    private String requiresPreAuthorize;
    private String modalTitle;
    private String modalDescription;
    private boolean requiresConfirmation = true;
    private io.github.yavonalabs.vectis.core.annotation.RiskLevel riskLevel = io.github.yavonalabs.vectis.core.annotation.RiskLevel.MODERATE;
    private BiConsumer<T, Map<String, String>> handler;

    private EntityAction(String id) {
        this.id = id;
        this.label = id;
    }

    public static <T> EntityAction<T> make(String id) {
        return new EntityAction<>(id);
    }

    public EntityAction<T> risk(io.github.yavonalabs.vectis.core.annotation.RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
        return this;
    }

    public EntityAction<T> label(String label) {
        this.label = label;
        return this;
    }

    public EntityAction<T> color(String color) {
        this.color = color;
        return this;
    }

    public EntityAction<T> icon(String icon) {
        this.icon = icon;
        return this;
    }

    public EntityAction<T> requiresRole(String role) {
        this.requiredRole = role;
        return this;
    }

    public EntityAction<T> requiresPreAuthorize(String preAuthorize) {
        this.requiresPreAuthorize = preAuthorize;
        return this;
    }

    public EntityAction<T> modalTitle(String title) {
        this.modalTitle = title;
        return this;
    }

    public EntityAction<T> modalDescription(String description) {
        this.modalDescription = description;
        return this;
    }

    public EntityAction<T> requiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
        return this;
    }

    public EntityAction<T> handler(BiConsumer<T, Map<String, String>> handler) {
        this.handler = handler;
        return this;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getColor() { return color; }
    public String getIcon() { return icon; }
    public String getRequiredRole() { return requiredRole; }
    public String getRequiresPreAuthorize() { return requiresPreAuthorize; }
    public String getModalTitle() { return modalTitle != null ? modalTitle : "Confirm Action: " + label; }
    public String getModalDescription() { return modalDescription != null ? modalDescription : "Are you sure you want to execute this action?"; }
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public io.github.yavonalabs.vectis.core.annotation.RiskLevel getRiskLevel() { return riskLevel; }
    public BiConsumer<T, Map<String, String>> getHandler() { return handler; }
}