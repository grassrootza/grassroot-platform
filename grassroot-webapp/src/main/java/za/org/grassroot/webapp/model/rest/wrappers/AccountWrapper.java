package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;

/**
 * Created by luke on 2017/01/11.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) @Getter @Setter
public class AccountWrapper {

    private final String uid;
    private final String createdByUserName;
    private final boolean createdByCallingUser;

    private final boolean enabled;

    private final String name;

    private final int groupsLeft;
    private final int messagesLeft;

    public AccountWrapper(Account account, User callingUser, int groupsLeft, int messagesLeft) {
        this.uid = account.getUid();
        this.createdByUserName = account.getCreatedByUser().nameToDisplay();
        this.createdByCallingUser = account.getCreatedByUser().equals(callingUser);

        this.enabled = account.isEnabled();
        this.name = account.getAccountName();

        this.groupsLeft = groupsLeft;
        this.messagesLeft = messagesLeft;

    }

}
