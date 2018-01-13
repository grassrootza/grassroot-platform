package za.org.grassroot.core.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

import javax.persistence.*;
import java.time.Instant;

/*
Entity to handle inbound joining, which will occur via various means
Note: not using tags for this because of likely need to track and record performance,
and because of possibility of large volumes at once and then may drop performance on
array column because can't use it naturally (would need a key-value pair, or separate column
todo: migrate old join code to this in time
 */
@Entity
@Table(name = "group_join_code")
@Getter
public class GroupJoinCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Basic
    @CreatedDate
    @Column(name = "creation_time", updatable = false, nullable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "user_uid", referencedColumnName = "uid")
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "group_uid", referencedColumnName = "uid")
    private Group group;

    @Basic
    @Column(name = "code", length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    @Setter private JoinCodeType type;

    @Basic
    @Column(name = "active")
    @Setter private boolean active;

    @Basic
    @Column(name = "closed_time")
    @Setter private Instant closedTime;

    @ManyToOne
    @JoinColumn(name = "closing_user_uid", referencedColumnName = "uid")
    @Setter private User closingUser;

    @Basic
    @Column(name = "short_url", length = 30) // leaving it at 30 just in case (slim it in future)
    @Setter private String shortUrl;

    // we can count this from logs, _but_ there are some entry points where we don't
    // have a user yet (e.g., via web), and creating a log would be excessive, until/unless high demand for it
    @Basic
    @Column(name = "count_reads")
    @Setter private long countOfInboundUsers;

    @Version
    private Integer version;

    private GroupJoinCode() {
        // for JPA
    }

    public GroupJoinCode(User createdByUser, Group group, String code, JoinCodeType type) {
        this.creationTime = Instant.now();
        this.createdByUser = createdByUser;
        this.group = group;
        this.code = code.trim().toLowerCase();
        this.type = type;
        this.active = true;
        this.countOfInboundUsers = 0;
    }

    public void incrementInboundUses() {
        this.countOfInboundUsers++;
    }

    @Override
    public String toString() {
        return "GroupJoinCode{" +
                "id=" + id +
                ", creationTime=" + creationTime +
                ", createdByUser=" + createdByUser +
                ", group=" + group +
                ", code='" + code + '\'' +
                ", active=" + active +
                ", closedTime=" + closedTime +
                ", closingUser=" + closingUser +
                ", version=" + version +
                '}';
    }
}
