package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.domain.association.AssociationRequestEvent;
import za.org.grassroot.core.enums.AssocRequestEventType;
import za.org.grassroot.core.enums.AssocRequestStatus;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.AccountSponsorshipRequestRepository;
import za.org.grassroot.core.repository.AssociationRequestEventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by luke on 2017/02/06.
 */
@Service
public class AccountSponsorshipBrokerImpl implements AccountSponsorshipBroker {

    private static final Logger logger = LoggerFactory.getLogger(AccountSponsorshipBrokerImpl.class);

    @Value("${grassroot.sponsorship.response.url:http://localhost:8080/account/sponsor/respond}")
    private String urlForResponse;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountSponsorshipRequestRepository requestRepository;
    private final AssociationRequestEventRepository requestEventRepository;
    private final MessageSourceAccessor messageSource;
    private final EmailSendingBroker emailSendingBroker;

    @Autowired
    public AccountSponsorshipBrokerImpl(UserRepository userRepository, AccountRepository accountRepository,
                                        AccountSponsorshipRequestRepository requestRepository, AssociationRequestEventRepository requestEventRepository,
                                        @Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSource, EmailSendingBroker emailSendingBroker) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.requestEventRepository = requestEventRepository;
        this.emailSendingBroker = emailSendingBroker;
        this.messageSource = messageSource;
    }

    @Override
    @Transactional
    public void openSponsorshipRequest(String openingUserUid, String accountUid, String destinationUserUid, String messageToUser) {
        Objects.requireNonNull(openingUserUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(destinationUserUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User requestedUser = userRepository.findOneByUid(destinationUserUid);
        User openingUser = userRepository.findOneByUid(openingUserUid);

        if (requestedUser == null || StringUtils.isEmpty(requestedUser.getEmailAddress())) {
            throw new IllegalArgumentException("User for sponsorship request must have an email address!");
        }

        logger.info("Opening new sponsorship request ...");

        AccountSponsorshipRequest request = new AccountSponsorshipRequest(account, requestedUser, messageToUser);
        requestRepository.save(request);

        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.OPENED, request, openingUser, Instant.now()));

        final String subject = messageSource.getMessage("email.sponsorship.subject", new String[] { account.getName() });
        final String amount = "R" + (new DecimalFormat("#,###.##").format(account.calculatePeriodCost() / 100));
        final String body = messageSource.getMessage("email.sponsorship.request", new String[] { requestedUser.getName(),
                account.getName(), amount, urlForResponse + "?requestUid=" + request.getUid() });
        final String message = messageSource.getMessage("email.sponsorship.message", new String[]{ openingUser.getName(), messageToUser });
        final String ending = messageSource.getMessage("email.sponsorship.ending");

        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder(subject)
                .address(requestedUser.getEmailAddress())
                .content(body + message + ending);

        emailSendingBroker.sendMail(builder.build());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountSponsorshipRequest load(String requestUid) {
        return requestRepository.findOneByUid(requestUid);
    }

    @Override
    @Transactional
    public void denySponsorshipRequest(String requestUid) {
        Objects.requireNonNull(requestUid);
        DebugUtil.transactionRequired("");

        AccountSponsorshipRequest request = requestRepository.findOneByUid(requestUid);
        request.setStatus(AssocRequestStatus.DECLINED);

        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.DECLINED, request,
                request.getDestination(), Instant.now()));

        final String subject = messageSource.getMessage("email.sponsorship.denied.subject");
        // note : since the sponsorship was not approved, the billing user will still be the one that opened the account
        final String[] fields = { request.getRequestor().getBillingUser().getName(), request.getDestination().getName(),
            "https://app.grassroot.org.za/tryagain" };

        emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                .address(request.getRequestor().getBillingUser().getEmailAddress())
                .content(messageSource.getMessage("email.sponsorship.denied.body", fields))
                .build());
    }

    @Override
    @Transactional
    public void approveRequestPaymentComplete(String requestUid) {
        Objects.requireNonNull(requestUid);
        DebugUtil.transactionRequired("");

        AccountSponsorshipRequest request = requestRepository.findOneByUid(requestUid);
        request.setStatus(AssocRequestStatus.APPROVED);
        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.APPROVED, request,
                request.getDestination(), Instant.now()));

        Account account = request.getRequestor();
        User newBillingUser = request.getDestination();

        account.addAdministrator(newBillingUser);
        account.setBillingUser(newBillingUser);
        newBillingUser.setPrimaryAccount(account);

        final String subject = messageSource.getMessage("email.sponsorship.approved.subject");
        final String[] fields = new String[2];
        fields[1] = "https://app.grassroot.org.za/send a thank you";
        request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()))
                .forEach(u -> {
                    fields[0] = u.getName();
                    emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                            .address(u.getEmailAddress())
                            .content(messageSource.getMessage("email.sponsorship.approved.body", fields))
                            .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserBeenAskedToSponsor(String userUid, String accountUid) {
        logger.info("checking if this user has a request pending .... ");
        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        return requestRepository.countByRequestorAndDestinationAndStatus(account, user, AssocRequestStatus.PENDING) > 0;
    }

    @Override
    @Transactional
    public void abortSponsorshipRequest(String requestUid) {
        Objects.requireNonNull(requestUid);
        DebugUtil.transactionRequired("");

        AccountSponsorshipRequest request = requestRepository.findOneByUid(requestUid);
        if (!AssocRequestStatus.APPROVED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Can only abort an approved request!");
        }

        request.setStatus(AssocRequestStatus.PENDING); // resets to this
        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.ABORTED, request,
                request.getDestination(), Instant.now()));

        final String subject = messageSource.getMessage("email.sponsorship.aborted.subject");
        final String fieldsDest[] = { request.getDestination().getName(), request.getRequestor().getName(),
            "https://app.grassroot.org.za/really need to do links" };
        final String fieldsReq[] = new String[3];
        fieldsReq[1] = request.getDestination().getName();
        fieldsReq[2] = "https://link here";

        emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                .address(request.getDestination().getEmailAddress())
                .content(messageSource.getMessage("email.sponsorship.aborted.body.dest", fieldsDest))
                .build());

        request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()))
                .forEach(u -> {
                    fieldsReq[0] = u.getName();
                    emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                            .address(u.getEmailAddress())
                            .content(messageSource.getMessage("email.sponsorship.aborted.body.req", fieldsReq))
                            .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSponsorshipRequest> requestsForUser(String userUid, AssocRequestStatus status, Sort sort) {
        User destination = userRepository.findOneByUid(userUid);
        return requestRepository.findByDestinationAndStatus(destination, status, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSponsorshipRequest> requestsForAccount(String accountUid, AssocRequestStatus status, Sort sort) {
        Account requestor = accountRepository.findOneByUid(accountUid);
        return requestRepository.findByRequestorAndStatus(requestor, status, sort);
    }

    @Override
    @Transactional
    public void closeOutstandingRequestsForAccount(String userUid, String accountUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        closeRequest(user, requestRepository.findByRequestorAndStatus(account, AssocRequestStatus.PENDING,
                new Sort(Sort.Direction.DESC, "creationTime")));
    }

    private void closeRequest(User user, List<AccountSponsorshipRequest> requests) {
        DebugUtil.transactionRequired("");
        Set<AssociationRequestEvent> events = new HashSet<>();
        requests.forEach(r -> {
            Instant time = Instant.now(); // so request processed time and log are synced
            r.setStatus(AssocRequestStatus.CLOSED);
            r.setProcessedTime(time);
            events.add(new AssociationRequestEvent(AssocRequestEventType.CLOSED, r, user, time));
        });

        requestEventRepository.save(events);
    }

}
