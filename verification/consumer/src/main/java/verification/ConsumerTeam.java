package verification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import io.github.yavonalabs.vectis.core.annotation.AdminEntity;

@Entity
@AdminEntity
public class ConsumerTeam {
    @Id private Long id = 1L;
    private String name = "Private team label";
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
}
