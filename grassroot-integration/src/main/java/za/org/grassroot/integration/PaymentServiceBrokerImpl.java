package za.org.grassroot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.integration.domain.PaymentMethod;
import za.org.grassroot.integration.domain.PeachPaymentResponse;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Set;

/**
 * Created by luke on 2016/10/26.
 */
@Service
@PropertySource("file:${user.home}/grassroot/grassroot-payments.properties")
public class PaymentServiceBrokerImpl implements PaymentServiceBroker {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceBrokerImpl.class);

    private AccountRepository accountRepository;
    private AccountBillingRecordRepository billingRepository;
    private RestTemplate restTemplate;
    private Environment environment;

    private UriComponentsBuilder baseUriBuilder;

    @Value("${grassroot.payments.host}")
    private String paymentsRestHost;
    @Value("${grassroot.payments.params.auth.user}")
    private String paymentsAuthUserIdParam;
    @Value("${grassroot.payments.params.auth.password}")
    private String paymentsAuthPasswordParam;
    @Value("${grassroot.payments.params.auth.channelId}")
    private String paymentsAuthChannelIdParam;

    @Value("${grassroot.payments.initial.path}")
    private String initialPaymentRestPath;
    @Value("${grassroot.payments.recurring.path}")
    private String recurringPaymentRestPath;

    @Value("${grassroot.payments.params.amount}")
    private String paymentAmountParam;
    @Value("${grassroot.payments.params.currency}")
    private String paymentCurrencyParam;
    @Value("${grassroot.payments.params.brand}")
    private String paymentCardBrand;
    @Value("${grassroot.payments.params.type}")
    private String paymentTypeParam;

    @Value("${grassroot.payments.params.cardnumber}")
    private String cardNumberParam;
    @Value("${grassroot.payments.params.holder}")
    private String cardHolderParam;
    @Value("${grassroot.payments.params.expiry.month}")
    private String cardExpiryMonthParam;
    @Value("${grassroot.payments.params.expiry.year}")
    private String cardExpiryYearParam;
    @Value("${grassroot.payments.params.cvv}")
    private String securityCodeParam;
    @Value("${grassroot.payments.params.recurring}")
    private String recurringParam;

    @Value("${grassroot.payments.values.user}")
    private String userId;
    @Value("${grassroot.payments.values.password}")
    private String password;
    @Value("${grassroot.payments.values.channelId}")
    private String channelId;
    @Value("${grassroot.payments.values.currency}")
    private String currency;

    @Value("${grassroot.payments.types.debit}")
    private String debitPayment;
    @Value("${grassroot.payments.types.preauth}")
    private String preAuth;

    @Autowired
    public PaymentServiceBrokerImpl(AccountRepository accountRepository, AccountBillingRecordRepository billingRepository,
                                    RestTemplate restTemplate, Environment environment) {
        this.accountRepository = accountRepository;
        this.billingRepository = billingRepository;
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        baseUriBuilder = UriComponentsBuilder.newInstance()
                .host(paymentsRestHost)
                .queryParam(paymentsAuthUserIdParam, userId)
                .queryParam(paymentsAuthPasswordParam, password)
                .queryParam(paymentsAuthChannelIdParam, channelId)
                .queryParam(paymentCurrencyParam, currency);
    }

    @Override
    @Transactional
    public boolean linkPaymentMethodToAccount(PaymentMethod paymentMethod, String accountUid) {
        /* if (environment.acceptsProfiles("localpg", "staging")) {
            Account account = accountRepository.findOneByUid(accountUid);
            account.setPaymentRef("payment_ref_here");
            account.setEnabledDateTime(Instant.now());
        } else {*/
            UriComponentsBuilder paymentUri = baseUriBuilder.cloneBuilder()
                    .path(initialPaymentRestPath)
                    .queryParam(paymentAmountParam, "100")
                    .queryParam(paymentCardBrand, "VISA")
                    .queryParam(paymentTypeParam, preAuth)
                    .queryParam(cardNumberParam, paymentMethod.getCardNumber())
                    .queryParam(cardHolderParam, paymentMethod.getCardHolder())
                    .queryParam(cardExpiryMonthParam, paymentMethod.getExpiryMonth())
                    .queryParam(cardExpiryYearParam, paymentMethod.getExpiryYear())
                    .queryParam(securityCodeParam, paymentMethod.getSecurityCode())
                    .queryParam(recurringParam, "true");

            PeachPaymentResponse response = restTemplate.getForObject(paymentUri.build().toUri(), PeachPaymentResponse.class);
            logger.info("okay! got this result back: " + response.toString());
        return true;
        //}
        // if production, call a payment method.
        //return true;
    }

    // todo : make sure to update the payment record and the account (remove amount from balance etc)
    @Override
    @Transactional
    public void processAccountPaymentsOutstanding() {
        Set<AccountBillingRecord> billsDue = billingRepository.findByNextPaymentDateBeforeAndPaidFalse(Instant.now());
        for (AccountBillingRecord record : billsDue) {
            // uh, wire up a payments provider ...
            UriComponentsBuilder paymentsUri = baseUriBuilder.cloneBuilder() // todo : use path variable in here
                    .queryParam(paymentAmountParam, record.getAmountToPay());
            restTemplate.getForObject(paymentsUri.build().toUri(), Object.class);
        }
    }

}
