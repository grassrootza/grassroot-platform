package za.org.grassroot.integration.billing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BillingServiceBroker {

    Flux<SubscriptionRecordDTO> fetchListOfSubscriptions(boolean activeOnly, String authToken);

    Mono<SubscriptionRecordDTO> createSubscription(String accountName, String billingAddress, String authToken, boolean active);

    Mono<SubscriptionRecordDTO> enableSubscription(String subscriptionId, String authToken);

    Mono<SubscriptionRecordDTO> cancelSubscription(String subscriptionId, String authToken); // ID in whatever managed service does accounts

    Mono<Boolean> isSubscriptionValid(String subscriptionId);

}
