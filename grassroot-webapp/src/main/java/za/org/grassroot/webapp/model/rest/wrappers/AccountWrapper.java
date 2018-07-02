package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final Set<String> groupNames;
    private final Set<String> adminNames;

    public AccountWrapper(Account account, User callingUser) {
        this.uid = account.getUid();
        this.createdByUserName = account.getCreatedByUser().nameToDisplay();
        this.createdByCallingUser = account.getCreatedByUser().equals(callingUser);

        this.enabled = account.isEnabled();
        this.name = account.getAccountName();

        // these are pretty inefficient but this should be occassional - turn into a query at some point
        this.groupNames = account.getPaidGroups().stream().map(Group::getName).collect(Collectors.toSet());
        this.adminNames = account.getAdministrators().stream().map(User::getName).collect(Collectors.toSet());

    }

}
