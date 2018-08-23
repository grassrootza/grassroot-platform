package za.org.grassroot.integration.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Objects;

import static za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse.SUCCESS_MATCHER;

/**
 * Created by luke on 2016/10/26.
 */
@Service
@ConditionalOnProperty("grassroot.payments.enabled")
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class PaymentBrokerImpl implements PaymentBroker {

    private static final Logger logger = LoggerFactory.getLogger(PaymentBrokerImpl.class);

    private RestTemplate restTemplate;

    @Value("${grassroot.payments.url:http://paymentsurl.com}")
    private String paymentUrl;
    @Value("${grassroot.payments.values.user:grassroot}")
    private String userId;
    @Value("${grassroot.payments.values.password:grasroot}")
    private String password;
    @Value("${grassroot.payments.values.channelId:testChannel}")
    private String channelId;
    @Value("${grassroot.payments.values.channelId3d:testChannel2}")
    private String entityId;

    @PostConstruct
    public void init() {
        logger.info("Initiated payment broker, URL: {}", paymentUrl);
    }

    @Autowired
    public PaymentBrokerImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentCopyPayResponse initiateCopyPayCheckout(int amountZAR, boolean recurring) {
        logger.info("Initiating a payment for {}, recurring? : {}", amountZAR, recurring);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(paymentUrl + "/v1/checkouts")
                .queryParam("authentication.userId", userId)
                .queryParam("authentication.password", password)
                .queryParam("authentication.entityId", entityId)
                .queryParam("amount", amountZAR + ".00")
                .queryParam("currency", "ZAR")
                .queryParam("paymentType", "DB");

        if (recurring) {
            builder = builder.queryParam("recurringType", "INITIAL")
                    .queryParam("createRegistration", "true");
        }

        HttpHeaders stdHeaders = new HttpHeaders();
        stdHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        logger.info("initiating a checkout, URL: {}", builder.build().toUri().toString());

        ResponseEntity<PaymentCopyPayResponse> response = restTemplate.exchange(
                builder.build().toUri(), HttpMethod.POST, new HttpEntity<>(stdHeaders), PaymentCopyPayResponse.class);
        return response.getBody();
    }

    @Override
    public PaymentCopyPayResponse getPaymentResult(String resourcePath, boolean storeRecurringResult, String accountUid) {
        if (storeRecurringResult) {
            Objects.requireNonNull(accountUid);
        }

        URI requestUri = UriComponentsBuilder.fromUriString(paymentUrl + resourcePath).build().toUri();
        logger.info("requesting payment result via URI: {}", requestUri.toString());
        try {
            ResponseEntity<PaymentCopyPayResponse> response = restTemplate.getForEntity(requestUri, PaymentCopyPayResponse.class);

            boolean successful = response.getBody() != null && SUCCESS_MATCHER.matcher(response.getBody().getInternalCode()).find();
            if (successful && storeRecurringResult) {
                logger.info("successful, and we have an account ID, so it should get stored");
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error retrieving payment result: {}", e.getMessage());
            return null;
        }
    }


}
