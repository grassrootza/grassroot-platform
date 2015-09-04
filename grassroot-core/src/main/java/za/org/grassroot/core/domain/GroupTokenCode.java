package za.org.grassroot.core.domain;


import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Created by luke on 2015/08/30.
 * Going to capture username in case we want a "cancellation" function and need to check it was the user who created
 * However, principal connection is to group.
 * todo: switch to using UserId, to accelerate lookups? methods/queries in here may be among the most used in application
 */
@Entity
@Table(name = "group_token_code")
@Inheritance(strategy = InheritanceType.JOINED)
public class GroupTokenCode extends TokenCode implements Serializable {

    private Group group;
    // private User creatingUser;

    private static final int DAY_MILLIS = 24 * 60 * 60 * 60 * 1000;

    @OneToOne(mappedBy="groupTokenCode")
    @PrimaryKeyJoinColumn
    public Group getGroup() { return group; }

    public void setGroup(Group group) { this.group = group; }

    // public User getCreatingUser() { return creatingUser; }
    // public void setCreatingUser(User creatingUser) { this.creatingUser = creatingUser; }

    public GroupTokenCode() {
    }

    public GroupTokenCode(Group group, User creatingUser, String code) {
        this.code = code;
        this.group = group;
        // this.creatingUser = creatingUser;
    }

    @Transient
    public GroupTokenCode withCodeExpiry(String code, Group relevantGroup, User creatingUser, Timestamp codeExpiry) {
        this.code = code;
        // this.creatingUser = creatingUser;
        this.expiryDateTime = codeExpiry;
        return this;
    }

    @Transient
    public GroupTokenCode withCodeExpiryDays(String code, Integer expiryDays) {
        this.code = code;
        this.expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + expiryDays * DAY_MILLIS);
        return this;
    }
}
