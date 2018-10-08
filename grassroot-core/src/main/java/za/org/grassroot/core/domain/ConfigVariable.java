package za.org.grassroot.core.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;

/*
Very general class used to store some dynamic pieces of config, e.g., free use limits, some backend URLs, etc.
Using this to replace large number of currently hard-coded variables. Will update based on a scheduled job.
_Could_ do this as some kind of simpler key-value store, or some complex arrangement with caching, but this is going
to be very occasionally used, and is very ligthweight, so trading off one more table / entity vs complexity of alternates
 */
@Entity
@Table(name="config_variable") @Getter
public class ConfigVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    protected Long id;

    @Column(name = "created_date_time", updatable = false, nullable = false)
    private Instant creationTime;

    @Column(name = "key_col", nullable = false, updatable = false, unique = true)
    private String key;

    @Column(name = "update_date_time", updatable = true)
    private Instant lastUpdatedTime;

    @Column(name = "value_col", nullable = false)
    private String value; // storing as a value so can be generic; parse on decoding

    @Column(name = "description")
    private String description;

    public void setValue(String value) {
        this.lastUpdatedTime = Instant.now();
        this.value = value;
    }

    private ConfigVariable() {
        // for JPA
    }

    public ConfigVariable(String key, String value,String description) {
        this.key = key;
        this.value = value;
        this.creationTime = Instant.now();
        this.lastUpdatedTime = this.creationTime;
        this.description = description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
