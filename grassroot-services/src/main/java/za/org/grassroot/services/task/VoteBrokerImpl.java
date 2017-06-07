package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.VoteResultsNotification;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.security.InvalidParameterException;
import java.time.Instant;
import java.util.*;

import static za.org.grassroot.core.specifications.EventLogSpecifications.*;
import static za.org.grassroot.core.util.StringArrayUtil.joinStringList;
import static za.org.grassroot.core.util.StringArrayUtil.listToArray;

/**
 * Created by luke on 2017/05/31.
 */
@Service
public class VoteBrokerImpl implements VoteBroker {

    private static final Logger logger = LoggerFactory.getLogger(VoteBrokerImpl.class);

    @Value("${grassroot.vote.option.maxlength:20}")
    private int MAX_OPTION_LENGTH;

    private static final String YES = "YES";
    private static final String NO = "NO";
    private static final String ABSTAIN = "ABSTAIN";
    private static final List<String> optionsForYesNoVote = Arrays.asList(YES, NO, ABSTAIN);

    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final EventLogRepository eventLogRepository;
    private final MessageAssemblingService messageService;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public VoteBrokerImpl(UserRepository userRepository, VoteRepository voteRepository, EventLogRepository eventLogRepository, PermissionBroker permissionBroker, MessageAssemblingService messageService, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
        this.eventLogRepository = eventLogRepository;
        this.messageService = messageService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Override
    public Vote load(String voteUid) {
        return voteRepository.findOneByUid(voteUid);
    }

    @Override
    @Transactional
    public void addVoteOption(String userUid, String voteUid, String voteOption) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(voteUid);

        User user = userRepository.findOneByUid(userUid);
        Vote vote = voteRepository.findOneByUid(voteUid);

        validateUserPermissionToModify(user, vote);

        if (StringUtils.isEmpty(voteOption)) {
            throw new IllegalArgumentException("Error! Vote option cannot be an empty string");
        }

        if (voteOption.length() > MAX_OPTION_LENGTH) {
            throw new InvalidParameterException("Error! Vote option description is too long");
        }

        vote.addVoteOption(voteOption);
        eventLogRepository.save(new EventLog(user, vote, EventLogType.VOTE_OPTION_ADDED, voteOption));
    }

    @Override
    @Transactional
    public void setListOfOptions(String userUid, String voteUid, List<String> options) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(voteUid);
        Objects.requireNonNull(options);

        User user = userRepository.findOneByUid(userUid);
        Vote vote = voteRepository.findOneByUid(voteUid);

        validateUserPermissionToModify(user, vote);

        vote.setTags(listToArray(options));
        EventLog eventLog = options.isEmpty() ?
                new EventLog(user, vote, EventLogType.VOTE_SET_YES_NO) :
                new EventLog(user, vote, EventLogType.VOTE_OPTIONS_SET, joinStringList(options, ", ", 250));
        eventLogRepository.save(eventLog);
    }

    @Override
    @Transactional
    public void recordUserVote(String userUid, String voteUid, String voteOption) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(voteUid);
        Objects.requireNonNull(voteOption);

        User user = userRepository.findOneByUid(userUid);
        Vote vote = voteRepository.findOneByUid(voteUid);

        validateUserPartOfVote(user, vote, true);

        EventLog priorResponse = eventLogRepository
                .findByEventAndUserAndEventLogType(vote, user, EventLogType.VOTE_OPTION_RESPONSE);

        if (priorResponse == null) {
            // user has not voted before, so adding a new one
            eventLogRepository.save(new EventLog(user, vote, EventLogType.VOTE_OPTION_RESPONSE, voteOption));
        } else {
            priorResponse.setTag(voteOption);
        }
    }

    @Override
    @Transactional
    public void calculateAndSendVoteResults(String voteUid) {
        Objects.requireNonNull(voteUid);
        Vote vote = voteRepository.findOneByUid(voteUid);
        logger.info("Sending vote results for {}", vote.getName());
        try {
            Map<String, Long> voteResults = StringArrayUtil.isAllEmptyOrNull(vote.getVoteOptions()) ?
                    calculateYesNoResults(vote) : calculateMultiOptionResults(vote, vote.getVoteOptions());

            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            EventLog eventLog = new EventLog(null, vote, EventLogType.RESULT);
            Set<User> voteResultsNotificationSentMembers = new HashSet<>(
                    userRepository.findNotificationTargetsForEvent(vote, VoteResultsNotification.class));

            vote.getAllMembers().stream()
                    .filter(u -> !voteResultsNotificationSentMembers.contains(u))
                    .forEach(u -> {
                        String msg = messageService.createMultiOptionVoteResultsMessage(u, vote, voteResults);
                        bundle.addNotification(new VoteResultsNotification(u, msg, eventLog));
                    });

            if (!bundle.getNotifications().isEmpty()) {
                bundle.addLog(eventLog);
            }

            logsAndNotificationsBroker.storeBundle(bundle);
        } catch (Exception e) {
            // just adding this since method is called in a stream / lambda so need to make sure
            // no exception interrupts it (else will spill over)
            logger.error("Error while sending vote results for vote " + vote + ": " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> fetchVoteResults(String userUid, String voteUid) {
        Objects.requireNonNull(voteUid);
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Vote vote = voteRepository.findOneByUid(voteUid);

        validateUserPartOfVote(user, vote, false);

        return StringArrayUtil.isAllEmptyOrNull(vote.getVoteOptions()) ?
                calculateYesNoResults(vote) : calculateMultiOptionResults(vote, vote.getVoteOptions());
    }

    private Map<String, Long> calculateMultiOptionResults(Vote vote, List<String> options) {
        Map<String, Long> results = new LinkedHashMap<>();
        List<EventLog> eventLogs = eventLogRepository.findAll(Specifications.where(isResponseToVote(vote)));
        options.forEach(o -> results.put(o, eventLogs.stream().filter(el -> o.equals(el.getTag())).count()));
        return results;
    }

    private Map<String, Long> calculateYesNoResults(Vote vote) {
        // vote may have been done via old method, so need to do a check, for now, if there are no
        // option responses (note: if no responses at all, it will still return valid result, since we
        // know at this point that it is a yes/no vote)
        return eventLogRepository.count(Specifications.where(ofType(EventLogType.VOTE_OPTION_RESPONSE))) == 0 ?
                calculateOldVoteResult(vote) :
                calculateMultiOptionResults(vote, optionsForYesNoVote); // will just return
    }

    private Map<String, Long> calculateOldVoteResult(Vote vote) {
        List<EventLog> eventLogs = eventLogRepository.findAll(Specifications.where(
                ofType(EventLogType.RSVP)).and(forEvent(vote)));
        Map<String, Long> results = new LinkedHashMap<>();
        results.put(YES, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.YES)).count());
        results.put(NO, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.NO)).count());
        results.put(ABSTAIN, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.MAYBE)).count());
        return results;
    }

    private void validateUserPermissionToModify(User user, Vote vote) {
        if (!vote.getCreatedByUser().equals(user)) {
            Membership userMembership = vote.getThisOrAncestorGroup().getMembership(user);
            if (userMembership == null ||
                    !userMembership.getRole().getName().equals(BaseRoles.ROLE_GROUP_ORGANIZER)) {
                throw new AccessDeniedException("Error! Only vote caller or group organizer can modify");
            }
        }
    }

    // not yet checking if vote has already been cast, since we allow for vote revision
    // (at the moment), and hence just check start date and part of vote members
    private void validateUserPartOfVote(User user, Vote vote, boolean checkVoteOpen) {
        if (checkVoteOpen && vote.getEventStartDateTime().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Error! Cannot respond to a closed vote");
        }

        boolean isUserInGroup = vote.isAllGroupMembersAssigned() ?
                vote.getThisOrAncestorGroup().hasMember(user) :
                vote.getAssignedMembers().contains(user);

        if (!isUserInGroup) {
            throw new AccessDeniedException("Only users part of group or assigned to vote can view or respond to vote");
        }
    }
}
