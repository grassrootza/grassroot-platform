package za.org.grassroot.integration.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.payments.peachp.PaymentCopyPayResponse;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
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
    private AsyncRestTemplate asyncRestTemplate;
    private HttpHeaders stdHeaders;

    @Value("${grassroot.payments.lambda.url:http://lambdas/payments}")
    private String paymentStorageLambdaEndpoint;

    @Value("${grassroot.payments.url:http://paymentsurl.com}")
    private String paymentUrl;

    @Value("${grassroot.payments.host:localhost}")
    private String paymentsRestHost;
    @Value("${grassroot.payments.params.auth.user:user}")
    private String paymentsAuthUserIdParam;
    @Value("${grassroot.payments.params.auth.password:pwd}")
    private String paymentsAuthPasswordParam;
    @Value("${grassroot.payments.params.auth.channelId:channel}")
    private String paymentsAuthChannelIdParam;

    @Value("${grassroot.payments.initial.path:/paytest}")
    private String initialPaymentRestPath;
    @Value("${grassroot.payments.recurring.path:/paytest}")
    private String recurringPaymentRestPath; // make a format string so can include registration ID appropriately

    @Value("${grassroot.payments.params.amount:amount}")
    private String paymentAmountParam;
    @Value("${grassroot.payments.params.currency:currency}")
    private String paymentCurrencyParam;
    @Value("${grassroot.payments.params.brand:brand}")
    private String paymentCardBrand;
    @Value("${grassroot.payments.params.type:type}")
    private String paymentTypeParam;
    @Value("${grassroot.payments.params.statementId:transid}")
    private String paymentTransIdParam;

    @Value("${grassroot.payments.params.cardnumber:card}")
    private String cardNumberParam;
    @Value("${grassroot.payments.params.holder:name}")
    private String cardHolderParam;
    @Value("${grassroot.payments.params.expiry.month:month}")
    private String cardExpiryMonthParam;
    @Value("${grassroot.payments.params.expiry.year:year}")
    private String cardExpiryYearParam;
    @Value("${grassroot.payments.params.cvv:seccode}")
    private String securityCodeParam;
    @Value("${grassroot.payments.params.recurring:recurring}")
    private String recurringParam;
    @Value("${grassroot.payments.params.regflag:registered}")
    private String registrationFlag;

    @Value("${grassroot.payments.values.user:grassroot}")
    private String userId;
    @Value("${grassroot.payments.values.password:grasroot}")
    private String password;
    @Value("${grassroot.payments.values.channelId:testChannel}")
    private String channelId;
    @Value("${grassroot.payments.values.channelId3d:testChannel2}")
    private String entityId;
    @Value("${grassroot.payments.values.currency:ZAR}")
    private String currency;

    @Value("${grassroot.payments.email.address:payments@grassroot.org.za}")
    private String paymentsEmailNotification;

    @Value("${grassroot.payments.deposit.details:Details")
    private String depositDetails;

    @Autowired
    public PaymentBrokerImpl(RestTemplate restTemplate, AsyncRestTemplate asyncRestTemplate) {
        this.restTemplate = restTemplate;
        this.asyncRestTemplate = asyncRestTemplate;
    }

    @PostConstruct
    public void init() {
        stdHeaders = new HttpHeaders();
        stdHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        stdHeaders.add(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8");
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

            boolean successful = SUCCESS_MATCHER.matcher(response.getBody().getInternalCode()).find();
            if (successful && storeRecurringResult) {
                logger.info("successful, and we have an account ID, so store it");
                storeAccountPaymentReference(accountUid, response.getBody().getRegistrationId());
            }

            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error retrieving payment result: {}", e.getMessage());
            return null;
        }
    }

    private void storeAccountPaymentReference(String accountUid, String registrationId) {
        URI storeResultUri = UriComponentsBuilder.fromUriString(paymentStorageLambdaEndpoint + "/store/" + accountUid)
                .build().toUri();

        Map<String, String> body = new HashMap<>();
        body.put("registrationId", registrationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ListenableFuture<ResponseEntity<String>> call = asyncRestTemplate.postForEntity(storeResultUri, entity, String.class);
        call.addCallback(result -> logger.info("successfully called storage, response: {}", result),
                ex -> logger.error("and, we have a failure, response: {}", ex));
    }


}
