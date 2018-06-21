package za.org.grassroot.integration.billing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BillingServiceBroker {

    Flux<String> fetchListOfSubscriptions(boolean activeOnly, String authToken);

    Mono<Void> createSubscription(String accountName, String billingAddress);

    Mono<Void> cancelSubscription(String subscriptionId); // ID in whatever managed service does accounts

    Mono<Boolean> isSubscriptionValid(String subscriptionId);

}
