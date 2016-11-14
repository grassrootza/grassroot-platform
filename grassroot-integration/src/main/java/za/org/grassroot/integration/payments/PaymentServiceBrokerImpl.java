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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.integration.exception.PaymentMethodFailedException;
import za.org.grassroot.integration.exception.PaymentMethodNot3dSecureException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Created by luke on 2016/10/26.
 */
@Service
@PropertySource(value = "file:${user.home}/grassroot/grassroot-payments.properties", ignoreResourceNotFound = true)
public class PaymentServiceBrokerImpl implements PaymentServiceBroker {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceBrokerImpl.class);

    private static final String MONTH_FORMAT = "%1$02d";
    private static final String YEAR_FORMAT = "20%d";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#.00");

    private static final String PRE_AUTH = "PA";
    private static final String DEBIT = "DB";
    private static final String RECURRING = "PA";

    private static final String INITIAL = "INITIAL";
    private static final String REPEAT = "REPEATED";

    private static final String OKAY_CODE = "000.100.110";

    private AccountBillingRecordRepository billingRepository;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

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
    @Value("${grassroot.payments.recurring.path:/paytset}")
    private String recurringPaymentRestPath; // make a format string so can include registration ID appropriately

    @Value("${grassroot.payments.params.amount:amount}")
    private String paymentAmountParam;
    @Value("${grassroot.payments.params.currency:currency}")
    private String paymentCurrencyParam;
    @Value("${grassroot.payments.params.brand:brand}")
    private String paymentCardBrand;
    @Value("${grassroot.payments.params.type:type}")
    private String paymentTypeParam;

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

    @Autowired
    public PaymentServiceBrokerImpl(AccountBillingRecordRepository billingRepository,
                                    RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.billingRepository = billingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
    public boolean linkPaymentMethodToAccount(PaymentMethod paymentMethod, String accountUid, AccountBillingRecord billingRecord, boolean deleteBillOnFailure) {
        Objects.requireNonNull(paymentMethod);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(billingRecord);

        final double amountToPay = (double) billingRecord.getTotalAmountToPay() / 100;

        logger.info("About to charge R{} to payment method, from billing record for {}", amountToPay, billingRecord.getTotalAmountToPay());

        UriComponentsBuilder paymentUri = generateInitialPaymentUri(paymentMethod, amountToPay);

        try {
            HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
            // logger.info("URL: " + paymentUri.toUriString());
            ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(paymentUri.build().toUri(), HttpMethod.POST, request, PaymentResponsePP.class);
            PaymentResponsePP okayResponse = response.getBody();
            logger.info("Payment Success!: With reference : {}", okayResponse.getRegistrationId());

            Account account = billingRecord.getAccount();
            account.setPaymentRef(okayResponse.getRegistrationId());
            handleSuccessfulPayment(account, billingRecord, okayResponse);

            return true;
        } catch (HttpStatusCodeException e) {
            try {
                PaymentErrorPP errorResponse = objectMapper.readValue(e.getResponseBodyAsString(), PaymentErrorPP.class);
                logger.info("Payment Error!: {}", errorResponse.toString());
            } catch (IOException error) {
                logger.info("Could not read in JSON!");
                error.printStackTrace();
            }
            // use this if the billing record is closely tied to the payment (e.g., on sign up, or switching), to prevent
            // duplication of the bill if there is failure and try again
            if (deleteBillOnFailure) {
                billingRepository.delete(billingRecord);
            }
            return false;
        }
    }

    private UriComponentsBuilder generateInitialPaymentUri(PaymentMethod paymentMethod, double amountToPay) {
        return baseUriBuilder.cloneBuilder()
                .path(initialPaymentRestPath)
                .queryParam(paymentAmountParam, AMOUNT_FORMAT.format(amountToPay))
                .queryParam(paymentCardBrand, paymentMethod.getCardBrand())
                .queryParam(paymentTypeParam, DEBIT)
                .queryParam(cardNumberParam, paymentMethod.getCardNumber())
                .queryParam(cardHolderParam, paymentMethod.getCardHolder())
                .queryParam(cardExpiryMonthParam, String.format(MONTH_FORMAT, paymentMethod.getExpiryMonth()))
                .queryParam(cardExpiryYearParam, String.format(YEAR_FORMAT, paymentMethod.getExpiryYear()))
                .queryParam(securityCodeParam, paymentMethod.getSecurityCode())
                .queryParam(recurringParam, INITIAL)
                .queryParam(registrationFlag, "true");
    }

    @Override
    public PaymentRedirectPP asyncPaymentInitiate(PaymentMethod method, double amountToPay, String returnToUrl) {
        try {
            UriComponentsBuilder uriToCall = generateInitialPaymentUri(method, amountToPay)
                    .queryParam(paymentsAuthChannelIdParam, channelId3d)
                    .queryParam("shopperResultUrl", returnToUrl);
            HttpEntity<PaymentResponsePP> request = new HttpEntity<>(stdHeaders);
            logger.info("URL: " + uriToCall.toUriString());
            ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(uriToCall.build().toUri(), HttpMethod.POST,
                    request, PaymentResponsePP.class);
            logger.info("RESPONSE: {}", response.toString());
            PaymentRedirectPP redirectPP = response.getBody().getRedirect();
            if (redirectPP != null) {
                return redirectPP;
            } else {
                throw new PaymentMethodNot3dSecureException();
            }
        } catch (HttpStatusCodeException e) {
            e.printStackTrace();
            try {
                PaymentErrorPP errorResponse = objectMapper.readValue(e.getResponseBodyAsString(), PaymentErrorPP.class);
                logger.info("Payment Error!: {}", errorResponse.toString());
                throw new PaymentMethodFailedException(errorResponse);
            } catch (IOException error) {
                error.printStackTrace();
                throw new PaymentMethodFailedException(null);
            }
        }
    }

    @Override
    @Transactional
    public void processAccountPaymentsOutstanding() {
        Set<AccountBillingRecord> billsDue = billingRepository.findByNextPaymentDateBeforeAndPaidFalse(Instant.now());
        for (AccountBillingRecord record : billsDue) {
            triggerRecurringPayment(record);
        }
    }

    private boolean triggerRecurringPayment(AccountBillingRecord billingRecord) {
        Account account = billingRecord.getAccount();
        final String recurringPaymentPathVar = String.format(recurringPaymentRestPath, account.getPaymentRef());
        final double amountToPay = (double) billingRecord.getTotalAmountToPay() / 100;
        UriComponentsBuilder paymentUri = baseUriBuilder.cloneBuilder()
                .path(recurringPaymentPathVar)
                .queryParam(paymentAmountParam,  AMOUNT_FORMAT.format(amountToPay))
                .queryParam(paymentTypeParam, RECURRING)
                .queryParam(recurringParam, REPEAT);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<PaymentResponsePP> request = new HttpEntity<>(headers);
            // logger.info("URL: " + paymentUri.toUriString());
            ResponseEntity<PaymentResponsePP> response = restTemplate.exchange(paymentUri.build().toUri(), HttpMethod.POST, request, PaymentResponsePP.class);
            PaymentResponsePP okayResponse = response.getBody();
            logger.info("Payment Success!: {}", okayResponse.toString());

            if (OKAY_CODE.equals(okayResponse.getResult().getCode())) {
                handleSuccessfulPayment(account, billingRecord, okayResponse);
            }

            return false;
        } catch (HttpStatusCodeException e) {
            handlePaymentError(e);
            return false;
        }
    }

    @Transactional
    private void handleSuccessfulPayment(Account account, AccountBillingRecord record, PaymentResponsePP response) {
        account.setLastPaymentDate(Instant.now());
        account.decreaseBalance(record.getTotalAmountToPay());
        record.setPaid(true);
        record.setPaidDate(Instant.now());
        record.setPaymentId(response.getId());
        record.setPaidAmount(response.getAmount() == null ? 0
                : (long) (response.getAmount() * 100));
    }

    private PaymentErrorPP handlePaymentError(HttpStatusCodeException e) {
        try {
            PaymentErrorPP errorResponse = objectMapper.readValue(e.getResponseBodyAsString(), PaymentErrorPP.class);
            logger.info("Payment Error!: {}", errorResponse.toString());
            return errorResponse;
        } catch (IOException error) {
            logger.info("Could not read in JSON!");
            error.printStackTrace();
            return null;
        }
    }

}
