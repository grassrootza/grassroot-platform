package za.org.grassroot.webapp.controller.rest.account;

import io.swagger.annotations.ApiModel;
import lombok.Getter;

import java.time.Instant;

@ApiModel @Getter
public class AccountBillingRecordResponse {
    private Instant createdDateTime;

    private String paymentId;

    public AccountBillingRecordResponse(Instant createdDateTime, String paymentId) {
        this.createdDateTime = createdDateTime;
        this.paymentId = paymentId;
    }
}
