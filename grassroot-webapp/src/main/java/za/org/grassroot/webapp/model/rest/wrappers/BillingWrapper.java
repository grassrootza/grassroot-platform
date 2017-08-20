package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.core.domain.account.AccountBillingRecord;

/**
 * Created by luke on 2017/01/19.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingWrapper {

    private String uid;
    private String accountUid;

    private long createdDateTimeMillis;
    private long statementDateTimeMillis;
    private long billedPeriodStartMillis;
    private long billedPeriodEndMillis;

    private long nextPaymentDateMillis;
    private long paidDateMillis;

    private long openingBalance;
    private long amountBilledThisPeriod;
    private long totalAmountToPay;

    private boolean paid;
    private long paidAmount;
    private String paymentId;
    private String paymentDescription;

    public BillingWrapper(AccountBillingRecord record) {

        this.uid = record.getUid();
        this.accountUid = record.getAccount().getUid();

        this.createdDateTimeMillis = record.getCreatedDateTime().toEpochMilli();
        this.statementDateTimeMillis = record.getStatementDateTime() != null ? record.getStatementDateTime().toEpochMilli() : -1;
        this.billedPeriodStartMillis = record.getBilledPeriodStart().toEpochMilli();
        this.billedPeriodEndMillis = record.getBilledPeriodEnd().toEpochMilli();

        this.nextPaymentDateMillis = record.getNextPaymentDate() != null ? record.getNextPaymentDate().toEpochMilli() : -1;
        this.paidDateMillis = record.getPaidDate() != null ? record.getPaidDate().toEpochMilli() : -1;

        this.openingBalance = record.getOpeningBalance();
        this.amountBilledThisPeriod = record.getAmountBilledThisPeriod();
        this.totalAmountToPay = record.getTotalAmountToPay();

        this.paid = record.getPaid();
        this.paidAmount = record.getPaidAmount() != null ? record.getPaidAmount() : 0;
        this.paymentId = record.getPaymentId();
        this.paymentDescription = record.getPaymentDescription();
    }

    public String getUid() {
        return uid;
    }

    public String getAccountUid() {
        return accountUid;
    }

    public long getCreatedDateTimeMillis() {
        return createdDateTimeMillis;
    }

    public long getStatementDateTimeMillis() {
        return statementDateTimeMillis;
    }

    public long getBilledPeriodStartMillis() {
        return billedPeriodStartMillis;
    }

    public long getBilledPeriodEndMillis() {
        return billedPeriodEndMillis;
    }

    public long getNextPaymentDateMillis() {
        return nextPaymentDateMillis;
    }

    public long getPaidDateMillis() {
        return paidDateMillis;
    }

    public long getOpeningBalance() {
        return openingBalance;
    }

    public long getAmountBilledThisPeriod() {
        return amountBilledThisPeriod;
    }

    public long getTotalAmountToPay() {
        return totalAmountToPay;
    }

    public boolean isPaid() {
        return paid;
    }

    public long getPaidAmount() {
        return paidAmount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPaymentDescription() {
        return paymentDescription;
    }
}
