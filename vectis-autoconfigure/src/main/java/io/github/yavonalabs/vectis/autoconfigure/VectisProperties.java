package io.github.yavonalabs.vectis.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vectis")
public class VectisProperties {

    private boolean enabled = true;
    private String path = "/admin";
    private String title = "Vectis";
    private List<String> roles = List.of("ROLE_ADMIN");
    private List<String> readOnlyRoles = List.of();
    private List<String> allowedEntities = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getRoles() {
        return roles;
    }
    public List<String> getReadOnlyRoles() { return readOnlyRoles; }
    public void setReadOnlyRoles(List<String> roles) { this.readOnlyRoles = roles; }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getAllowedEntities() {
        return allowedEntities;
    }

    public void setAllowedEntities(List<String> allowedEntities) {
        this.allowedEntities = allowedEntities;
    }
}
