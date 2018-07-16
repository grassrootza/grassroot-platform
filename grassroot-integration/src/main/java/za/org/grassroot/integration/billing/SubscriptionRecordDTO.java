package za.org.grassroot.integration.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class SubscriptionRecordDTO {

    private String id;

    private String customerId;

    @JsonProperty("account_name")
    private String accountName;

    private String plan;

    private String status;

    @JsonProperty("due_invoices")
    private int numberInvoicesDue;

    @JsonProperty("next_billing")
    private long nextBillingEpochSeconds;


}
