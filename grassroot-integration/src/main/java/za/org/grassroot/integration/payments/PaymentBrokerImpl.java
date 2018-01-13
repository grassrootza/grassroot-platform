package za.org.grassroot.integration.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.exception.PaymentMethodFailedException;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.payments.peachp.PaymentErrorPP;
import za.org.grassroot.integration.payments.peachp.PaymentResponsePP;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Objects;

/**
 * Created by luke on 2016/10/26.
 */
@Service
@PropertySource(value = "${grassroot.payments.properties}", ignoreResourceNotFound = true)
public class PaymentBrokerImpl implements PaymentBroker {

    private static final Logger logger = LoggerFactory.getLogger(PaymentBrokerImpl.class);

    private static final String MONTH_FORMAT = "%1$02d";
    private static final String YEAR_FORMAT = "20%d";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#.00");

    private static final String PRE_AUTH = "PA";
    private static final String DEBIT = "DB";
    private static final String RECURRING = "DB";

    private static final String INITIAL = "INITIAL";
    private static final String REPEAT = "REPEATED";

    private static final String OKAY_CODE = "000.100.110";
    private static final String NO_3D_CODE = "100.390.109";
    private static final String ZEROS_CODE = "000.000.000";

    private AccountBillingRecordRepository billingRepository;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private final MessagingServiceBroker messageBroker;

    private UriComponentsBuilder baseUriBuilder;
    private HttpHeaders stdHeaders;

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
    private String channelId3d;
    @Value("${grassroot.payments.values.currency:ZAR}")
    private String currency;

    @Value("${grassroot.payments.email.address:payments@grassroot.org.za}")
    private String paymentsEmailNotification;

    @Value("${grassroot.payments.deposit.details:Details")
    private String depositDetails;

    @Autowired
    public PaymentBrokerImpl(AccountBillingRecordRepository billingRepository,
                             RestTemplate restTemplate, ObjectMapper objectMapper, MessagingServiceBroker messageBroker) {
        this.billingRepository = billingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.messageBroker = messageBroker;
    }

    @PostConstruct
    public void init() {
        baseUriBuilder = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(paymentsRestHost)
                .queryParam(paymentsAuthUserIdParam, userId)
                .queryParam(paymentsAuthPasswordParam, password)
                .queryParam(paymentCurrencyParam, currency);

        stdHeaders = new HttpHeaders();
        stdHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        stdHeaders.add(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8");
    }

    @Override
    @Transactional
    public PaymentResponse asyncPaymentInitiate(String accountUid, PaymentMethod method, AccountBillingRecord amountToPay, String returnToUrl) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(method);
        Objects.requireNonNull(returnToUrl);

        try {
            UriComponentsBuilder uriToCall = generateInitialPaymentUri(method, amountToPay.getTotalAmountToPay())
                    .queryParam(paymentsAuthChannelIdParam, channelId3d)
                    .queryParam(paymentTransIdParam, amountToPay.getUid())
                    .queryParam("shopperResultUrl", returnToUrl);
            HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
            logger.info("URL: " + uriToCall.toUriString());
            ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(uriToCall.build().toUri(), HttpMethod.POST,
                    request, PaymentResponsePP.class);
            logger.info("RESPONSE: {}", response.toString());
            PaymentResponsePP paymentResponse = response.getBody();
            amountToPay.setPaymentId(paymentResponse.getThisPaymentId());
            if (StringUtils.isEmpty(paymentResponse.getRedirectUrl())) {
                handleSuccessfulPayment(amountToPay, paymentResponse);
            }
            return paymentResponse;
        } catch (HttpStatusCodeException e) {
            amountToPay.setNextPaymentDate(null);
            handlePaymentInitError(e, amountToPay);
            return null; // will throw error before getting here
        }
    }

    @Override
    @Transactional
    public PaymentResponse initiateMobilePayment(AccountBillingRecord record, String notificationUrl) {
        Objects.requireNonNull(record);

        try {
          UriComponentsBuilder uriToCall = baseUriBuilder.cloneBuilder()
                  .path("/v1/checkouts")
                  .queryParam(paymentAmountParam, AMOUNT_FORMAT.format(record.getTotalAmountToPay() / 100.00))
                  .queryParam(paymentsAuthChannelIdParam, channelId)
                  .queryParam(paymentTypeParam, DEBIT)
                  .queryParam(recurringParam, INITIAL)
                  .queryParam("notificationUrl", notificationUrl)
                  .queryParam("shopperResultUrl", notificationUrl)
                  .queryParam(registrationFlag, "true");

          HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
          final URI formedUrl = uriToCall.build().toUri();
          logger.info("In mobile payments, about to call ... {}", formedUrl.toString());
          ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(uriToCall.build().toUri(), HttpMethod.POST,
                  request, PaymentResponsePP.class);
          logger.info("Mobile response: " + response.toString());
          PaymentResponsePP paymentResponse = response.getBody();
          record.setPaymentId(paymentResponse.getThisPaymentId());
          return paymentResponse;
        } catch (HttpStatusCodeException e) {
            record.setNextPaymentDate(null); // to make sure this isn't tried again by accident
            handlePaymentInitError(e, record);
            return null; // will throw error before getting here
        }
    }

    private void handlePaymentInitError(HttpStatusCodeException e, AccountBillingRecord record) throws PaymentMethodFailedException {
        try {
            PaymentErrorPP errorResponse = objectMapper.readValue(e.getResponseBodyAsString(), PaymentErrorPP.class);
            logger.info("Payment Error!: {}", errorResponse.toString());
            record.setPaymentDescription(errorResponse.getResult().fullDescription());
            throw new PaymentMethodFailedException(errorResponse);
        } catch (IOException error) {
            error.printStackTrace();
            throw new PaymentMethodFailedException(null);
        } finally {
            sendFailureEmail(record, e.getResponseBodyAsString());
        }
    }

    @Override
    @Transactional
    public PaymentResponse asyncPaymentCheckResult(String paymentId, String resourcePath) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(paymentsRestHost)
                .path(resourcePath)
                .queryParam(paymentsAuthUserIdParam, userId)
                .queryParam(paymentsAuthPasswordParam, password)
                .queryParam(paymentsAuthChannelIdParam, channelId3d);

        logger.info("Calling URI: " + builder.toUriString());

        HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
        ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET,
                request, PaymentResponsePP.class);
        logger.info("Async response: {}", response.toString());
        return handlePaymentCheckCleanUp(response, paymentId);
    }

    @Override
    @Transactional
    public PaymentResponse checkMobilePaymentResult(String paymentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(paymentsRestHost)
                .path("/v1/checkouts")
                .pathSegment(paymentId)
                .pathSegment("payment")
                .queryParam(paymentsAuthUserIdParam, userId)
                .queryParam(paymentsAuthPasswordParam, password)
                .queryParam(paymentsAuthChannelIdParam, channelId);

        logger.info("checking payment status with URL: {}", builder.toUriString());

        HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
        ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET,
                request, PaymentResponsePP.class);
        logger.info("Mobile check response: {}", response.toString());
        return handlePaymentCheckCleanUp(response, paymentId);
    }

    private PaymentResponse handlePaymentCheckCleanUp(ResponseEntity<PaymentResponsePP> response, final String paymentId) {
        DebugUtil.transactionRequired("PaymentService");

        if (response.getStatusCode().is2xxSuccessful() && response.getBody().getResult() != null) {
            PaymentResponsePP responsePP = response.getBody();
            AccountBillingRecord record = billingRepository.findOneByPaymentId(paymentId);
            if (responsePP.getResult().isSuccessful()) {
                handleSuccessfulPayment(record, responsePP);
            } else {
                record.setPaymentDescription(responsePP.getResult().fullDescription());
                sendFailureEmail(record, response.getBody().toString());
            }
            return responsePP;
        } else {
            logger.info("Error in response! {}", response.toString());
            sendFailureEmail(null, response.toString());
            return new PaymentResponse(PaymentResultType.FAILED_OTHER, paymentId);
        }
    }

    private UriComponentsBuilder generateInitialPaymentUri(PaymentMethod paymentMethod, double amountToPay) {
        return baseUriBuilder.cloneBuilder()
                .path(initialPaymentRestPath)
                .queryParam(paymentAmountParam, AMOUNT_FORMAT.format(amountToPay / 100.00))
                .queryParam(paymentCardBrand, paymentMethod.getCardBrand())
                .queryParam(paymentTypeParam, DEBIT)
                .queryParam(cardNumberParam, paymentMethod.normalizedCardNumber())
                .queryParam(cardHolderParam, paymentMethod.getCardHolder())
                .queryParam(cardExpiryMonthParam, String.format(MONTH_FORMAT, paymentMethod.getExpiryMonth()))
                .queryParam(cardExpiryYearParam, String.format(YEAR_FORMAT, paymentMethod.getExpiryYear()))
                .queryParam(securityCodeParam, paymentMethod.getSecurityCode())
                .queryParam(recurringParam, INITIAL)
                .queryParam(registrationFlag, "true");
    }

    @Override
    public String fetchDetailsForDirectDeposit(String accountUid) {
        // todo : generate an account reference
        return depositDetails;
    }

    @Override
    @Transactional
    public boolean triggerRecurringPayment(AccountBillingRecord billingRecord) {
        Account account = billingRecord.getAccount();
        if (StringUtils.isEmpty(account.getPaymentRef())) {
            billingRecord.setNextPaymentDate(null);
            return false;
        }

        final String recurringPaymentPathVar = String.format(recurringPaymentRestPath, account.getPaymentRef());
        final double amountToPay = (double) billingRecord.getTotalAmountToPay() / 100;
        logger.info("Triggering recurring payment for : {}", account.getAccountName());

        UriComponentsBuilder paymentUri = baseUriBuilder.cloneBuilder()
                .path(recurringPaymentPathVar)
                .queryParam(paymentsAuthChannelIdParam, channelId)
                .queryParam(paymentAmountParam,  AMOUNT_FORMAT.format(amountToPay))
                .queryParam(paymentTypeParam, RECURRING)
                .queryParam(recurringParam, REPEAT);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<PaymentResponsePP> request = new HttpEntity<>(headers);

            ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(paymentUri.build().toUri(), HttpMethod.POST, request, PaymentResponsePP.class);
            PaymentResponsePP okayResponse = response.getBody();
            logger.info("Payment Success!: {}", okayResponse.toString());

            final String resultCode = okayResponse.getResult().getCode();
            if (OKAY_CODE.equals(resultCode) || ZEROS_CODE.equals(resultCode)) {
                handleSuccessfulPayment(billingRecord, okayResponse);
                return true;
            } else {
                handlePaymentError(billingRecord, okayResponse.getResult().getCode(), okayResponse.getResult().getDescription());
                return false;
            }
        } catch (HttpStatusCodeException e) {
            handlePaymentError(billingRecord, e);
            return false;
        }
    }

    private void handleSuccessfulPayment(AccountBillingRecord record, PaymentResponsePP response) {
        DebugUtil.transactionRequired("");

        Account account = record.getAccount();
        account.setLastPaymentDate(Instant.now());
        account.decreaseBalance(record.getTotalAmountToPay());
        record.setPaid(true);
        record.setPaidDate(Instant.now());
        record.setPaymentId(response.getId());
        record.setPaidAmount(response.getAmount() == null ? 0 : (long) (response.getAmount() * 100));

        final String desc = StringUtils.isEmpty(response.getResult().getDescription()) ?
                response.getDescription() : response.getResult().getDescription();
        record.setPaymentDescription(response.getResult().getCode() + ": " + desc);

        String email = "A payment succeeded for account " + account.getAccountName() + ", with amount paid as "
                + (record.getTotalAmountToPay() / 100) + ". Next billing date is " + account.getNextBillingDate();
        messageBroker.sendEmail(Collections.singletonList(paymentsEmailNotification),
                new GrassrootEmail.EmailBuilder("Payment notification: successful").content(email).build());

    }

    private void handlePaymentError(AccountBillingRecord record, HttpStatusCodeException e) {
        String errorCode, description;
        try {
            PaymentErrorPP errorResponse = objectMapper.readValue(e.getResponseBodyAsString(), PaymentErrorPP.class);
            errorCode = errorResponse.getResult().getCode();
            description = errorResponse.getResult().getDescription();
        } catch (IOException io) {
            errorCode = "0";
            description = "Unknown, error processing response";
        }
        handlePaymentError(record, errorCode, description);
    }

    private void handlePaymentError(AccountBillingRecord record, String errorCode, String description) {
        DebugUtil.transactionRequired("");
        record.setNextPaymentDate(Instant.now().plus(3L, ChronoUnit.DAYS)); // so it doesn't try again  immediately
        final String errorDescription = errorCode + ": " + description;
        logger.info("Payment Error!: {}", errorDescription);
        record.setPaymentDescription(errorDescription);
        if (errorDescription.contains("100.150.101")) {
            Account account = record.getAccount();
            account.setPaymentRef(null); // todo: fix this (or just go with JS integration)
        }
        sendFailureEmail(record, errorDescription);
    }

    private void sendFailureEmail(AccountBillingRecord record, String errorBody) {
        String email = "A payment failed.";
        if (record != null) {
            email += "The payment was for " + record.getAccount().getAccountName() + ", with amount paid as "
                    + (record.getTotalAmountToPay() / 100) + ". \n";
        }
        email += "The error body received from the server was: \n" + errorBody;
        messageBroker.sendEmail(Collections.singletonList(paymentsEmailNotification),
                new GrassrootEmail.EmailBuilder("Payment notification: error!").content(email).build());
    }

}
