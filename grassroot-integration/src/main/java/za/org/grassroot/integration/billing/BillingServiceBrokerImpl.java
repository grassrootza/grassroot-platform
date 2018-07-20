package za.org.grassroot.integration.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

@Service
@ConditionalOnProperty("grassroot.billing.enabled")
public class BillingServiceBrokerImpl implements BillingServiceBroker {

    @Value("${grassroot.subscriptions.lambda.url:http://localhost:3000}")
    private String lambdaUrl;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.create(lambdaUrl);
    }

    @Override
    public Flux<SubscriptionRecordDTO> fetchListOfSubscriptions(boolean activeOnly, String authToken) {
        return webClient.get()
                .uri("/accounts/list")
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToFlux(SubscriptionRecordDTO.class);
    }

    @Override
    public Mono<SubscriptionRecordDTO> createSubscription(String accountName, String billingAddress, String authToken, boolean active) {
        return webClient.post()
                .uri("/account/create?accountName={name}&emailAddress={email}&active={active}", accountName, billingAddress, active)
                .header("Authorization", "Bearer ")
                .retrieve()
                .bodyToMono(SubscriptionRecordDTO.class);
    }

    @Override
    public Mono<SubscriptionRecordDTO> enableSubscription(String subscriptionId, String authToken) {
        return webClient.post()
                .uri("/account/enable?subscriptionId={subscriptionId}", subscriptionId)
                .header("Authorization" , "Bearer " + authToken)
                .retrieve()
                .bodyToMono(SubscriptionRecordDTO.class);
    }

    @Override
    public Mono<SubscriptionRecordDTO> cancelSubscription(String subscriptionId, String authToken) {
        return webClient.post()
                .uri("/account/cancel?subscriptionId={subscriptionId}", subscriptionId)
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToMono(SubscriptionRecordDTO.class);
    }

    @Override
    public Mono<Boolean> isSubscriptionValid(String subscriptionId) {
        return webClient.post()
                .uri("/account/validity?subscriptionId={subscriptionId}", subscriptionId)
                .retrieve()
                .bodyToMono(Boolean.class);
    }
}
