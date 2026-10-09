package verification;

import jakarta.persistence.*;
import io.github.yavonalabs.vectis.core.annotation.*;
import io.github.yavonalabs.vectis.core.action.ActionExecutionMode;
import java.util.Map;

@Entity
@AdminEntity
public class ConsumerRecord {
    @Id private Long id = 1L;
    @Version private Long version;
    @jakarta.validation.constraints.NotBlank
    private String name = "Independent consumer";
    @ManyToOne private ConsumerTeam team;
    private Integer completed = 0;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public ConsumerTeam getTeam() { return team; }
    public void setTeam(ConsumerTeam value) { team = value; }
    public Integer getCompleted() { return completed; }
    public void setCompleted(Integer value) { completed = value; }
    @AdminAction(label = "Complete once", executionMode = ActionExecutionMode.MANAGED_LOCAL, previewMethod = "preview")
    public void complete() { completed++; }
    public Map<String, Object> preview() { return Map.of("completed", completed + 1); }
}
