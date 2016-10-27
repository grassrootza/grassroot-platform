package za.org.grassroot.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.integration.domain.PaymentMethod;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Set;

/**
 * Created by luke on 2016/10/26.
 */
@Service
public class PaymentServiceBrokerImpl implements PaymentServiceBroker {

    private AccountRepository accountRepository;
    private AccountBillingRecordRepository billingRepository;
    private RestTemplate restTemplate;
    private Environment environment;

    private String paymentsRestProtocol;
    private String paymentsRestHost;
    private String paymentsRestPath;
    private String paymentsRestSubscriberParam;
    private String paymentsRestAmountParam;
    // todo : add any other parameters

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
        paymentsRestProtocol = environment.getProperty("grassroot.payments.rest.protocol", "https");
        paymentsRestHost = environment.getProperty("grassroot.payments.rest.host", "https://localhost:8080");
        paymentsRestPath = environment.getProperty("grassroot.payments.rest.path", "/payments/test");
        paymentsRestSubscriberParam = environment.getProperty("grassroot.payments.param.subscriber", "accountUid");
        paymentsRestAmountParam = environment.getProperty("grassroot.payments.param.amount", "amount");
    }

    @Override
    @Transactional
    public boolean linkPaymentMethodToAccount(PaymentMethod paymentMethod, String accountUid) {
        if (environment.acceptsProfiles("localpg", "staging")) {
            Account account = accountRepository.findOneByUid(accountUid);
            account.setPaymentRef("payment_ref_here");
            account.setEnabledDateTime(Instant.now());
        }
        // if production, call a payment method.
        return true;
    }

    // todo : make sure to update the payment record and the account (remove amount from balance etc)
    @Override
    @Transactional
    public void processAccountPaymentsOutstanding() {
        Set<AccountBillingRecord> billsDue = billingRepository.findByNextPaymentDateBeforeAndPaidFalse(Instant.now());
        for (AccountBillingRecord record : billsDue) {
            // uh, wire up a payments provider ...
            UriComponentsBuilder paymentsUri = UriComponentsBuilder.newInstance()
                    .scheme(paymentsRestProtocol)
                    .host(paymentsRestHost)
                    .path(paymentsRestPath)
                    .queryParam(paymentsRestSubscriberParam, record.getAccount().getPaymentRef())
                    .queryParam(paymentsRestAmountParam, record.getAmountToPay());
            restTemplate.getForObject(paymentsUri.build().toUri(), Object.class);
        }
    }

}
