package za.org.grassroot.core.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Class to hold a template, with a message, to trigger after a defined event (only allowed for GRExtra accounts)
 */
@Entity
@Table(name = "notification_template")
@Getter @Builder
public class NotificationTemplate {

    public static final int MAX_MESSAGES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "creation_time", insertable = true, updatable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 50, nullable = false)
    private NotificationTriggerType triggerType;

    @Basic
    @Column(name = "active")
    @Setter private boolean active;

    @Basic
    @Column(name = "only_use_free")
    @Setter private boolean onlyUseFreeChannels;

    @Basic
    @Column(name = "msg_template1", nullable = false)
    @Setter private String messageTemplate;

    @Basic
    @Column(name = "msg_template2")
    @Setter private String messageTemplate2;

    @Basic
    @Column(name = "msg_template3")
    @Setter private String messageTemplate3;

    @Column(name = "language")
    @Setter private Locale language;

    @Basic
    @Column(name = "send_delay")
    @Setter private Long delayIntervalMillis;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    @Setter private Account account;

    @ManyToOne
    @JoinColumn(name = "group_id")
    @Setter private Group group;

    @Basic
    @Column(name = "cascade")
    @Setter @Builder.Default private boolean cascade = false;

    @Tolerate
    private NotificationTemplate() {
        // for JPA
    }

    // using this to preserve builder pattern (note: deviates from prior style of setting these in constructor,
    // todo is consider reconciling to old way by more sophisticated use of Builder pattern
    @PrePersist
    private void setDefaults() {
        if (uid == null) {
            uid = UIDGenerator.generateId();
        }
        if (creationTime == null) {
            creationTime = Instant.now();
        }
    }

    public List<String> getTemplateStrings() {
        List<String> templates = new ArrayList<>();
        templates.add(messageTemplate);
        if (!StringUtils.isEmpty(messageTemplate2)) {
            templates.add(messageTemplate2);
        }
        if (!StringUtils.isEmpty(messageTemplate3)) {
            templates.add(messageTemplate3);
        }
        return templates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotificationTemplate template = (NotificationTemplate) o;

        return uid.equals(template.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}
