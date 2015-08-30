package za.org.grassroot.core.domain;

import javax.persistence.*;
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
public class GroupTokenCode extends TokenCode {

    private User creatingUser;
    private Group relevantGroup;

    private static final int DAY_MILLIS = 24 * 60 * 60 * 60 * 1000;

    public GroupTokenCode() {
    }

    public GroupTokenCode(Group relevantGroup, User creatingUser, String code) {
        this.code = code;
        this.relevantGroup = relevantGroup;
        this.creatingUser = creatingUser;
    }

    public GroupTokenCode withCodeExpiry(String code, Group relevantGroup, User creatingUser, Timestamp codeExpiry) {
        this.code = code;
        this.creatingUser = creatingUser;
        this.expiryDateTime = codeExpiry;
        return this;
    }

    public GroupTokenCode withCodeExpiryDays(String code, Integer expiryDays) {
        this.code = code;
        this.expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + expiryDays * DAY_MILLIS);
        return this;
    }

    @OneToOne
    @JoinColumn(name="relevant_group", referencedColumnName = "id")
    public Group getRelevantGroup() { return relevantGroup; }

    public void setRelevantGroup(Group relevantGroup) { this.relevantGroup = relevantGroup; }

    public User getCreatingUser() { return creatingUser; }

    public void setCreatingUser(User creatingUser) { this.creatingUser = creatingUser; }
}
