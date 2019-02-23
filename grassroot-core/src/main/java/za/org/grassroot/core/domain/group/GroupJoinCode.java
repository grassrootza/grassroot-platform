package za.org.grassroot.core.domain.group;

import za.org.grassroot.core.domain.User;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/*
Entity to handle inbound joining, which will occur via various means
Note: not using tags for this because of likely need to track and record performance,
and because of possibility of large volumes at once and then may drop performance on
array column because can't use it naturally (would need a key-value pair, or separate column
todo: migrate old join code to this in time
 */
@Entity
@Table(name = "group_join_code")
public class GroupJoinCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "creation_time", updatable = false, nullable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "active")
    private boolean active;

    @Column(name = "closed_time")
    private Instant closedTime;

    @ManyToOne
    @JoinColumn(name = "closing_user_id")
    private User closingUser;

    @Column(name = "short_url", length = 30) // leaving it at 30 just in case (slim it in future)
    private String shortUrl;

    // we can count this from logs, _but_ there are some entry points where we don't
    // have a user yet (e.g., via web), and creating a log would be excessive, until/unless high demand for it
    @Column(name = "count_reads")
    private long countOfInboundUsers;

    private GroupJoinCode() {
        // for JPA
    }

    public GroupJoinCode(User createdByUser, Group group, String code, String shortUrl) {
        this.creationTime = Instant.now();
        this.createdByUser = createdByUser;
        this.group = group;
        this.code = code.trim().toLowerCase();
        this.active = true;
        this.countOfInboundUsers = 0;
        this.shortUrl = shortUrl;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }

    public Group getGroup() {
        return group;
    }

    public Instant getClosedTime() {
        return closedTime;
    }

    public User getClosingUser() {
        return closingUser;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void close(Instant time, User user) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(user);

        this.active = false;
        this.closingUser = user;
        this.closedTime = time;
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
                '}';
    }
}
