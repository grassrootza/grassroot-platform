package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/09/08.
 */
@Entity
@Table(name = "messenger_settings")
public class GroupChatSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @Column(name = "active",nullable = false)
    private boolean active;


    @Column(name ="user_initiated", nullable = false)
    private boolean userInitiated;


    @Column(name="receive")
    private boolean canReceive;

    @Column(name="send")
    private boolean canSend;

    @Column(name="reactivation_time")
    private Instant reactivationTime;

    private GroupChatSettings(){}

    public GroupChatSettings(User user, Group group, boolean active, boolean userInitiated, boolean canSend, boolean canReceive){
        this.user = user;
        this.group=group;
        this.createdDateTime = Instant.now();
        this.active=active;
        this.userInitiated = userInitiated;
        this.canSend = canSend;
        this.canReceive=canReceive;
        this.reactivationTime=Instant.now();

    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Group getGroup() {
        return group;
    }


    public boolean isCanSend() {
        return canSend;
    }

    public boolean isUserInitiated() {
        return userInitiated;
    }

    public boolean isCanReceive() {
        return canReceive;
    }

    public Instant getReactivationTime() {
        return reactivationTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setUserInitiated(boolean userInitiated) {
        this.userInitiated = userInitiated;
    }

    public void setCanReceive(boolean canReceive) {
        this.canReceive = canReceive;
    }

    public void setCanSend(boolean canSend) {
        this.canSend = canSend;
    }

    public void setReactivationTime(Instant reactivationTime) {
        this.reactivationTime = reactivationTime;
    }
}
