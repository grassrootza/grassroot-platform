package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountType;

/**
 * Created by luke on 2017/01/11.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountWrapper {

    private final String uid;
    private final String createdByUserName;
    private final boolean createdByCallingUser;

    private final String billingUserName;
    private final boolean billedToCallingUser;

    private final boolean enabled;
    private final AccountType type;

    private final String name;
    private final int maxNumberGroups;
    private final int maxSizePerGroup;
    private final int maxSubGroupDepth;
    private final int todosPerGroupPerMonth;
    private final int freeFormMessages;

    private final long lastPaymentDateMilli;
    private final long nextBillingDateMilli;

    private final long outstandingBalance;
    private final int subscriptionFee;

    private final int groupsLeft;
    private final int messagesLeft;

    public AccountWrapper(Account account, User callingUser, int groupsLeft, int messagesLeft) {
        this.uid = account.getUid();
        this.createdByUserName = account.getCreatedByUser().nameToDisplay();
        this.createdByCallingUser = account.getCreatedByUser().equals(callingUser);

        this.billingUserName = account.getBillingUser() == null ? "" : account.getBillingUser().nameToDisplay();
        this.billedToCallingUser = account.getBillingUser() != null && account.getBillingUser().equals(callingUser);

        this.enabled = account.isEnabled();
        this.name = account.getAccountName();
        this.type = account.getType();

        this.maxNumberGroups = account.getMaxNumberGroups();
        this.maxSizePerGroup = account.getMaxSizePerGroup();
        this.maxSubGroupDepth = account.getMaxSubGroupDepth();
        this.todosPerGroupPerMonth = account.getTodosPerGroupPerMonth();
        this.freeFormMessages = account.getFreeFormMessages();

        this.lastPaymentDateMilli = account.getLastPaymentDate() == null ? 0 : account.getLastPaymentDate().toEpochMilli();
        this.nextBillingDateMilli = account.getNextBillingDate() == null ? 0 : account.getNextBillingDate().toEpochMilli();
        this.outstandingBalance = account.getOutstandingBalance();
        this.subscriptionFee = account.getSubscriptionFee();

        this.groupsLeft = groupsLeft;
        this.messagesLeft = messagesLeft;
    }

    public String getUid() {
        return uid;
    }

    public String getCreatedByUserName() {
        return createdByUserName;
    }

    public boolean isCreatedByCallingUser() {
        return createdByCallingUser;
    }

    public String getBillingUserName() {
        return billingUserName;
    }

    public boolean isBilledToCallingUser() {
        return billedToCallingUser;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() { return type; }

    public int getMaxNumberGroups() {
        return maxNumberGroups;
    }

    public int getMaxSizePerGroup() {
        return maxSizePerGroup;
    }

    public int getMaxSubGroupDepth() {
        return maxSubGroupDepth;
    }

    public int getTodosPerGroupPerMonth() {
        return todosPerGroupPerMonth;
    }

    public int getFreeFormMessages() {
        return freeFormMessages;
    }

    public long getLastPaymentDateMilli() {
        return lastPaymentDateMilli;
    }

    public long getNextBillingDateMilli() {
        return nextBillingDateMilli;
    }

    public long getOutstandingBalance() {
        return outstandingBalance;
    }

    public int getSubscriptionFee() {
        return subscriptionFee;
    }

    public int getGroupsLeft() {
        return groupsLeft;
    }

    public int getMessagesLeft() {
        return messagesLeft;
    }
}
