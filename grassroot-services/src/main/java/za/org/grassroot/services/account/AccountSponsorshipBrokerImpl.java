package za.org.grassroot.services.account;

import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Created by luke on 2017/02/06.
 */
@Service
public class AccountSponsorshipBrokerImpl implements AccountSponsorshipBroker {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountSponsorshipRequestRepository requestRepository;
    private final AssociationRequestEventRepository requestEventRepository;
    private final EmailSendingBroker emailSendingBroker;

    @Autowired
    public AccountSponsorshipBrokerImpl(UserRepository userRepository, AccountRepository accountRepository,
                                        AccountSponsorshipRequestRepository requestRepository, AssociationRequestEventRepository requestEventRepository,
                                        EmailSendingBroker emailSendingBroker) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.requestEventRepository = requestEventRepository;
        this.emailSendingBroker = emailSendingBroker;
    }

    @Override
    @Transactional
    public void openSponsorshipRequest(String openingUserUid, String accountUid, String requestedUserUid, String messageToUser) {
        Objects.requireNonNull(openingUserUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(requestedUserUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User requestedUser = userRepository.findOneByUid(requestedUserUid);
        User openingUser = userRepository.findOneByUid(openingUserUid);

        if (requestedUser == null || StringUtils.isEmpty(requestedUser.getEmailAddress())) {
            throw new IllegalArgumentException("User for sponsorship request must have an email address!");
        }

        AccountSponsorshipRequest request = new AccountSponsorshipRequest(account, requestedUser, messageToUser);
        requestRepository.save(request);

        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.OPENED, request, openingUser, Instant.now()));

        final String content = String.format("Hi, you've been asked to sponsor a Grassroot account for %s. The account" +
                        "type is %s, which allows for all these amazing things. Below is a message from the requestor: %s",
                account.getAccountName(), account.getType(), messageToUser);

        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder("Grassroot Sponsorship Request")
                .address(requestedUser.getEmailAddress())
                .from("sponsorships@grassroot.org.za")
                .content(content);

        emailSendingBroker.sendMail(builder.build());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountSponsorshipRequest fetchSponsorshipRequest(String requestUid) {
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

        // send an email notifying

    }

    @Override
    @Transactional
    public void approveSponsorshipRequest(String requestUid) {
        Objects.requireNonNull(requestUid);
        DebugUtil.transactionRequired("");

        AccountSponsorshipRequest request = requestRepository.findOneByUid(requestUid);
        request.setStatus(AssocRequestStatus.APPROVED);

        requestEventRepository.save(new AssociationRequestEvent(AssocRequestEventType.APPROVED, request,
                request.getDestination(), Instant.now()));

        // hand over to billing (only send email once succeeded ...)
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

        // send an email?
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
}
