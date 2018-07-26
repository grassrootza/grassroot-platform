package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final String subscriptionId;
    private final long lastBillingDateMillis;

    private final Map<String, String> paidForGroups;
    private final Map<String, String> otherAdmins;

    private final boolean primary;
    private Map<String, String> otherAccounts;

    private long notificationsSinceLastBill;
    private long chargedUssdSinceLastBill;

    private List<String> geoDataSets;

    public AccountWrapper(Account account, User user) {
        this.uid = account.getUid();
        this.createdByUserName = account.getCreatedByUser().nameToDisplay();
        this.createdByCallingUser = account.getCreatedByUser().equals(user);

        this.enabled = account.isEnabled();
        this.name = account.getAccountName();
        this.subscriptionId = account.getSubscriptionRef();
        this.lastBillingDateMillis = account.getLastBillingDate().toEpochMilli();

        // these are pretty inefficient but this should be occassional - turn into a query at some point
        this.paidForGroups = account.getPaidGroups().stream().collect(Collectors.toMap(Group::getUid, Group::getName));

        this.otherAdmins = account.getAdministrators().stream()
                .filter(Objects::nonNull)
                .filter(otherUser -> !user.equals(otherUser))
                .collect(Collectors.toMap(User::getUid, User::getName));

        this.primary = account.equals(user.getPrimaryAccount());
        if (user.getAccountsAdministered() != null && !user.getAccountsAdministered().isEmpty()) {
            this.otherAccounts = user.getAccountsAdministered().stream()
                    .filter(otherAccount -> !account.equals(otherAccount))
                    .filter(otherAccount -> !account.isClosed())
                    .collect(Collectors.toMap(Account::getUid, Account::getName));
        }

        this.geoDataSets = StringUtils.isEmpty(account.getGeoDataSets()) ? null :
                Arrays.asList(StringUtils.split(account.getGeoDataSets(), ","));
    }

}
