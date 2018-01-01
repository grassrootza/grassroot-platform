package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.domain.association.AssociationRequestEvent;
import za.org.grassroot.core.enums.AssocRequestEventType;
import za.org.grassroot.core.enums.AssocRequestStatus;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.AccountSponsorshipRequestRepository;
import za.org.grassroot.core.repository.AssociationRequestEventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.SponsorRequestSpecifications.*;

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
    private final AccountEmailService accountEmailService;
    private final MessagingServiceBroker messageBroker;

    @Autowired
    public AccountSponsorshipBrokerImpl(UserRepository userRepository, AccountRepository accountRepository,
                                        AccountSponsorshipRequestRepository requestRepository, AssociationRequestEventRepository requestEventRepository,
                                        AccountEmailService emailService, MessagingServiceBroker messageBroker) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.requestEventRepository = requestEventRepository;
        this.accountEmailService = emailService;
        this.messageBroker = messageBroker;
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
        AccountSponsorshipRequest request;

        if (hasUserBeenAskedToSponsor(destinationUserUid, accountUid)) {
            logger.info("Refreshing an existing sponsorship request ...");
            priorRequestExists = true;
            request = requestRepository.findOne(where(forAccount(account))
                    .and(toUser(requestedUser))
                    .and(hasStatus(Arrays.asList(AssocRequestStatus.PENDING, AssocRequestStatus.VIEWED))));

            AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.REMINDED, request, openingUser, Instant.now());
            event.setAuxDescription(request.getDescription()); // to store/log prior message (possibly useful for analytics in future)
            request.setDescription(messageToUser);

        } else {
            logger.info("Opening new sponsorship request ...");
            priorRequestExists = false;
            request = new AccountSponsorshipRequest(account, requestedUser, messageToUser);
            requestRepository.save(request);
            requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.OPENED, request, openingUser, Instant.now()));
        }

        final String requestLink = urlForResponse + "?requestUid=" + request.getUid();
        messageBroker.sendEmail(Collections.singletonList(requestedUser.getEmailAddress()),
                accountEmailService.createSponsorshipRequestMail(request, openingUser, messageToUser, priorRequestExists));

        if (!StringUtils.isEmpty(openingUser.getEmailAddress())) {
            messageBroker.sendEmail(Collections.singletonList(openingUser.getEmailAddress()),
                    accountEmailService.openingUserEmail(priorRequestExists, requestLink, requestedUser.getName(), openingUser));
        }
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

        messageBroker.sendEmail(Collections.singletonList(request.getRequestor().getBillingUser().getEmailAddress()),
                accountEmailService.sponsorshipDeniedEmail(request));
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
            List<String> addresses = approvedRequest.getRequestor().getAdministrators()
                    .stream()
                    .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()) && !u.equals(approvedRequest.getDestination()))
                    .map(User::getEmailAddress).collect(Collectors.toList());

            messageBroker.sendEmail(addresses, accountEmailService.sponsorshipApprovedEmail(approvedRequest));
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
            logger.info("Can only abort an approved request!");
        } else {
            request.setStatus(AssocRequestStatus.PENDING); // resets to this
            requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.ABORTED, request,
                    request.getDestination(), Instant.now()));

            messageBroker.sendEmail(Collections.singletonList(request.getDestination().getEmailAddress()),
                    accountEmailService.sponsorshipReminderEmailSponsor(request));

            accountEmailService.sponsorshipReminderEmailRequestor(request)
                    .forEach(e -> messageBroker.sendEmail(Collections.singletonList(e.getAddress()), e));
        }
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

}
