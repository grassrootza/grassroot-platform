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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.services.specifications.SponsorRequestSpecifications.*;

/**
 * Created by luke on 2017/02/06.
 */
@Service
public class AccountSponsorshipBrokerImpl implements AccountSponsorshipBroker {

    private static final Logger logger = LoggerFactory.getLogger(AccountSponsorshipBrokerImpl.class);

    @Value("${grassroot.sponsorship.response.url:http://localhost:8080/account/sponsor/respond}")
    private String urlForResponse;

    @Value("${grassroot.sponsorship.request.url:http://localhost:8080/account/sponsor/request}")
    private String urlForRequest;

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
    @Transactional(readOnly = true)
    public AccountSponsorshipRequest load(String requestUid) {
        return requestRepository.findOneByUid(requestUid);
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

        boolean priorRequestExists;
        String subjectKey, bodyKey;
        AccountSponsorshipRequest request;

        if (hasUserBeenAskedToSponsor(destinationUserUid, accountUid)) {
            logger.info("Refreshing an existing sponsorship request ..."); // todo : may want to limit this in future (prevent nagging)
            priorRequestExists = true;
            request = requestRepository.findOne(where(forAccount(account))
                    .and(toUser(requestedUser))
                    .and(hasStatus(Arrays.asList(AssocRequestStatus.PENDING, AssocRequestStatus.VIEWED))));

            AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.REMINDED, request, openingUser, Instant.now());
            event.setAuxDescription(request.getDescription()); // to store/log prior message (possibly useful for analytics in future)
            request.setDescription(messageToUser);

            subjectKey = "email.sponsorship.reminder.subject";
            bodyKey = "email.sponsorship.reminder.body";
        } else {
            logger.info("Opening new sponsorship request ...");
            priorRequestExists = false;

            request = new AccountSponsorshipRequest(account, requestedUser, messageToUser);
            requestRepository.save(request);
            requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.OPENED, request, openingUser, Instant.now()));

            subjectKey = "email.sponsorship.subject";
            bodyKey = "email.sponsorship.request";
        }

        final String requestLink = urlForResponse + "?requestUid=" + request.getUid();
        final String subject = messageSource.getMessage(subjectKey, new String[]{account.getName()});
        final String amount = "R" + (new DecimalFormat("#,###.##").format(account.calculatePeriodCost() / 100));
        final String body = messageSource.getMessage(bodyKey, new String[]{requestedUser.getName(),
                account.getName(), amount, requestLink});
        final String message = messageSource.getMessage("email.sponsorship.message", new String[]{openingUser.getName(), messageToUser});
        final String ending = messageSource.getMessage("email.sponsorship.ending");

        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder(subject)
                .address(requestedUser.getEmailAddress())
                .content(body + message + ending);

        emailSendingBroker.sendMail(builder.build());

        if (!StringUtils.isEmpty(openingUser.getEmailAddress())) {
            notifyOpeningUser(priorRequestExists, requestLink, requestedUser.getName(), openingUser);
        }
    }

    private void notifyOpeningUser(boolean alreadyOpen, final String requestLink, final String destinationName, final User openingUser) {
        final String subject = messageSource.getMessage("email.sponsorship.requested.subject");
        final String body = messageSource.getMessage(alreadyOpen ? "email.sponsorship.reminded.body" : "email.sponsorship.requested.body",
                new String[] { openingUser.getName(), destinationName, requestLink});

        emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                .address(openingUser.getEmailAddress())
                .content(body)
                .build());
    }

    @Override
    @Transactional
    public void markRequestAsViewed(String requestUid) {
        Objects.requireNonNull(requestUid);
        DebugUtil.transactionRequired("");

        AccountSponsorshipRequest request = requestRepository.findOneByUid(requestUid);
        request.setStatus(AssocRequestStatus.VIEWED);

        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.RESPONDING, request,
                request.getDestination(), Instant.now()));
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
                urlForRequest + "?accountUid=" + request.getRequestor().getUid() };

        emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                .address(request.getRequestor().getBillingUser().getEmailAddress())
                .content(messageSource.getMessage("email.sponsorship.denied.body", fields))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserBeenAskedToSponsor(String userUid, String accountUid) {
        logger.info("checking if this user has a request pending .... ");
        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        return requestRepository.count(where(forAccount(account))
                        .and(toUser(user))
                        .and(hasStatus(Arrays.asList(AssocRequestStatus.PENDING, AssocRequestStatus.VIEWED)))) > 0;
    }

    @Override
    @Transactional
    public void closeRequestsAndMarkApproved(String userUid, String accountUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        List<AccountSponsorshipRequest> requests = requestRepository.findAll(forAccount(account))
                .stream()
                .filter(r -> AssocRequestStatus.VIEWED.equals(r.getStatus()) || AssocRequestStatus.PENDING.equals(r.getStatus()))
                .collect(Collectors.toList());
        AccountSponsorshipRequest approvedRequest = closeRequests(true, user, requests);

        if (approvedRequest != null) {
            sendApprovedEmails(approvedRequest);
        }
    }


    @Override
    @Transactional
    public void abortAndCleanSponsorshipRequests() {
        DebugUtil.transactionRequired("");
        Instant viewedThreshold = Instant.now().minus(1L, ChronoUnit.DAYS);
        Instant pendingThreshold = Instant.now().minus(1L, ChronoUnit.DAYS);

        requestRepository.findAll(where(hasStatus(AssocRequestStatus.VIEWED))
                .and(createdBefore(viewedThreshold)))
                .forEach(this::handleAbortedRequest);

        closeRequests(false, null, requestRepository.findAll(
                where(hasStatus(AssocRequestStatus.PENDING)).and(createdBefore(pendingThreshold))
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accountHasOpenRequests(String accountUid) {
        Objects.requireNonNull(accountUid);
        Account account = accountRepository.findOneByUid(accountUid);
        return requestRepository.count(where(forAccount(account))
                .and(hasStatus(Arrays.asList(AssocRequestStatus.PENDING, AssocRequestStatus.VIEWED)))) > 0;
    }

    private void handleAbortedRequest(AccountSponsorshipRequest request) {
        DebugUtil.transactionRequired("");

        if (!AssocRequestStatus.APPROVED.equals(request.getStatus())) {
            throw new IllegalArgumentException("Can only abort an approved request!");
        }

        request.setStatus(AssocRequestStatus.PENDING); // resets to this
        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.ABORTED, request,
                request.getDestination(), Instant.now()));

        final String subject = messageSource.getMessage("email.sponsorship.aborted.subject");
        final String fieldsDest[] = { request.getDestination().getName(), request.getRequestor().getName(),
                urlForResponse + "?requestUid=" + request.getUid() };
        final String fieldsReq[] = new String[3];
        fieldsReq[1] = request.getDestination().getName();

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
    public List<AccountSponsorshipRequest> openRequestsForAccount(String accountUid, Sort sort) {
        Account requestor = accountRepository.findOneByUid(accountUid);
        return requestRepository.findAll(where(forAccount(requestor))
                .and(hasStatus(Arrays.asList(AssocRequestStatus.PENDING, AssocRequestStatus.VIEWED))), sort);
    }

    private AccountSponsorshipRequest closeRequests(boolean findApprovedRequest, User approvingUser, List<AccountSponsorshipRequest> requests) {
        DebugUtil.transactionRequired("");
        logger.info("inside sponsorship broker ... closing {} requests", requests.size());
        Set<AssociationRequestEvent> events = new HashSet<>();
        AccountSponsorshipRequest approvedRequest = null;
        for (AccountSponsorshipRequest r : requests) {
            boolean markApproved = findApprovedRequest && r.getDestination().equals(approvingUser);
            r.setStatus(markApproved ? AssocRequestStatus.APPROVED : AssocRequestStatus.CLOSED);
            Instant time = Instant.now(); // so request processed time and log are synced
            r.setProcessedTime(time);
            events.add(new AssociationRequestEvent(markApproved ? AssocRequestEventType.APPROVED : AssocRequestEventType.CLOSED,
                    r, approvingUser, time));
            if (markApproved) {
                approvedRequest = r;
            }
        }

        requestEventRepository.save(events);
        return approvedRequest;
    }

    private void sendApprovedEmails(AccountSponsorshipRequest request) {
        final String subject = messageSource.getMessage("email.sponsorship.approved.subject");
        final String[] fields = new String[2];
        // fields[1] = "https://app.grassroot.org.za/send a thank you";
        request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()) && !u.equals(request.getDestination()))
                .forEach(u -> {
                    fields[0] = u.getName();
                    emailSendingBroker.sendMail(new GrassrootEmail.EmailBuilder(subject)
                            .address(u.getEmailAddress())
                            .content(messageSource.getMessage("email.sponsorship.approved.body", fields))
                            .build());
                });
    }

}
