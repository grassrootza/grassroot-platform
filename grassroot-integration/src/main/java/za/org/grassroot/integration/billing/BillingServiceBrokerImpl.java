package za.org.grassroot.integration.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

public class BillingServiceBrokerImpl implements BillingServiceBroker {

    @Value("${grassroot.subscriptions.lambda.url:http://localhost:3000}")
    private String lambdaUrl;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.create(lambdaUrl);
    }

    @Override
    public Flux<String> fetchListOfSubscriptions(boolean activeOnly, String authToken) {
        return webClient.get()
                .uri("/accounts/list")
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToFlux(String.class);
    }

    @Override
    public Mono<Void> createSubscription(String accountName, String billingAddress) {
        return webClient.post()
                .uri("/account/create")
                .header("Authorization", "Bearer ")
                .retrieve()
                .bodyToMono(Void.class);
    }

    @Override
    public Mono<Void> cancelSubscription(String subscriptionId) {
        return webClient.post()
                .uri("/account/cancel")
                .header("Authorization", "Bearer ")
                .retrieve()
                .bodyToMono(Void.class);
    }

    @Override
    public Mono<Boolean> isSubscriptionValid(String subscriptionId) {
        return Mono.just(true);
    }
}
