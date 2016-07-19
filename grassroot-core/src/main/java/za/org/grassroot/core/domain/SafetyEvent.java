package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Created by paballo on 2016/07/18.
 */
@Entity
@Table(name="safety_event")
public class SafetyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;


    @Column(name = "created_date_time", nullable = false,insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne()
    @JoinColumn(name = "activated_by_user", nullable = false, updatable = false)
    private User activatedBy;

    @ManyToOne()
    @JoinColumn(name = "parent_group_id", nullable = false, updatable = false)
    private Group parentGroup;

    @Column(name = "reminderMinutes")
    private Integer reminderMinutes;



    private  SafetyEvent(){
    }

    public SafetyEvent(User activatedBy, Group parentGroup){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.activatedBy = activatedBy;
        this.parentGroup = parentGroup;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public User getActivatedBy() {
        return activatedBy;
    }

    public void setActivatedBy(User activatedBy) {
        this.activatedBy = activatedBy;
    }

    public Group getParentGroup() {
        return parentGroup;
    }

    public void setParentGroup(Group parentGroup) {
        this.parentGroup = parentGroup;
    }

    public Integer getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(Integer reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }
}
