package za.org.grassroot.services.task;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thymeleaf.util.MapUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.notification.UserLanguageNotification;
import za.org.grassroot.core.domain.notification.VoteResultsNotification;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.exception.TaskFinishedException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static za.org.grassroot.core.enums.EventLogType.CHANGE;
import static za.org.grassroot.core.specifications.EventLogSpecifications.*;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by luke on 2017/05/31.
 */
@Service
public class VoteBrokerImpl implements VoteBroker {

    private static final Logger logger = LoggerFactory.getLogger(VoteBrokerImpl.class);

    @Value("${grassroot.vote.option.maxlength:20}")
    private int MAX_OPTION_LENGTH;

    private static final Sort SORT_DIRECTION = Sort.by(Sort.Direction.ASC, "eventStartDateTime");

    private static final String YES = "YES";
    private static final String NO = "NO";
    private static final String ABSTAIN = "ABSTAIN";
    private static final List<String> optionsForYesNoVote = Arrays.asList(YES, NO, ABSTAIN);

    private final UserManagementService userService;
    private final VoteRepository voteRepository;
    private final EventLogRepository eventLogRepository;
    private final MembershipRepository membershipRepository;
    private final MessageAssemblingService messageService;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public VoteBrokerImpl(UserManagementService userService, VoteRepository voteRepository, EventLogRepository eventLogRepository, MembershipRepository membershipRepository, MessageAssemblingService messageService, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userService = userService;
        this.voteRepository = voteRepository;
        this.eventLogRepository = eventLogRepository;
        this.membershipRepository = membershipRepository;
        this.messageService = messageService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Override
    public Vote load(String voteUid) {
        return voteRepository.findOneByUid(voteUid);
    }

    @Override
    @Transactional
    public Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(voteUid);

        User user = userService.load(userUid);
        Vote vote = voteRepository.findOneByUid(voteUid);

        if (vote.isCanceled()) {
            throw new IllegalStateException("Vote is canceled: " + vote);
        }

        if (!vote.getCreatedByUser().equals(user)) {
            throw new AccessDeniedException("Error! Only user who created vote can update it");
        }

        Instant convertedClosingDateTime = convertToSystemTime(eventStartDateTime, getSAST());

        boolean startTimeChanged = !vote.getEventStartDateTime().equals(convertedClosingDateTime);
        if (startTimeChanged) {
            // removing this check because we are going to use this to close a vote without cancelling it
            //  validateVoteClosingTime(convertedClosingDateTime);
            vote.setEventStartDateTime(convertedClosingDateTime);
            vote.updateScheduledReminderTime();
        }

        vote.setDescription(description);

        // note: as of now, we don't need to send anything, hence just do an explicit call to repo and return the Vote

        Vote savedVote = voteRepository.save(vote);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        EventLog eventLog = new EventLog(user, vote, CHANGE, null, startTimeChanged);
        bundle.addLog(eventLog);

        logsAndNotificationsBroker.storeBundle(bundle);

        return savedVote;
    }

    @Override
    @Transactional
    public void recordUserVote(String userUid, String voteUid, String voteOption) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(voteUid);
        Objects.requireNonNull(voteOption);

        Vote vote = voteRepository.findOneByUid(voteUid);

        if (vote.getEventStartDateTime().isBefore(Instant.now())) {
            throw new TaskFinishedException();
        }

        User user = userService.load(userUid);

        validateUserPartOfVote(user, vote, true);

        EventLog priorResponse = eventLogRepository
                .findByEventAndUserAndEventLogType(vote, user, EventLogType.VOTE_OPTION_RESPONSE);

        final List<String> options = vote.getVoteOptions().isEmpty() ? Arrays.asList("YES", "NO", "ABSTAIN") : vote.getVoteOptions();

        Optional<String> storedOption = options.stream().filter(s -> s.trim().equalsIgnoreCase(voteOption.trim()))
                .findFirst();

        if (!storedOption.isPresent() || StringUtils.isEmpty(storedOption.get()))
            throw new IllegalArgumentException("Error! Non existent vote option passed to us");

        if (priorResponse == null) {
            // user has not voted before, so adding a new one
            eventLogRepository.save(new EventLog(user, vote, EventLogType.VOTE_OPTION_RESPONSE, storedOption.get()));
        } else {
            priorResponse.setTag(voteOption);
        }
    }

    @Override
    @Transactional
    public void calculateAndSendVoteResults(String voteUid) {
        Objects.requireNonNull(voteUid);
        Vote vote = voteRepository.findOneByUid(voteUid);
        if (vote.shouldStopNotifications()) {
            logger.info("Vote is set to halt notifications, aborting");
            eventLogRepository.save(new EventLog(null, vote, EventLogType.RESULT));
            return;
        }

        logger.info("Sending vote results for {}", vote.getName());
        try {
            Map<String, Long> voteResults = StringArrayUtil.isAllEmptyOrNull(vote.getVoteOptions()) ?
                    calculateYesNoResults(vote) : calculateMultiOptionResults(vote, vote.getVoteOptions());

            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            EventLog eventLog = new EventLog(null, vote, EventLogType.RESULT);
            Set<User> voteResultsNotificationSentMembers = new HashSet<>(
                    userService.findUsersNotifiedAboutEvent(vote, VoteResultsNotification.class));

            // in case we need it
            final String languageMessage = messageService.createMultiLanguageMessage();

            vote.getAllMembers().stream()
                    .filter(u -> !voteResultsNotificationSentMembers.contains(u))
                    .peek(u -> {
                        String msg = messageService.createMultiOptionVoteResultsMessage(u, vote, voteResults);
                        bundle.addNotification(new VoteResultsNotification(u, msg, eventLog));
                    })
                    .filter(userService::shouldSendLanguageText)
                    .forEach(u -> {
                        logger.info("Along with vote results, notifying a user about multiple languages ...");
                        UserLog userLog = new UserLog(u.getUid(), UserLogType.NOTIFIED_LANGUAGES, null, UserInterfaceType.SYSTEM);
                        bundle.addLog(userLog);
                        bundle.addNotification(new UserLanguageNotification(u, languageMessage, userLog));
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
    public Map<String, Long> fetchVoteResults(String userUid, String voteUid, boolean swallowMemberException) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        User user = userService.load(Objects.requireNonNull(userUid));
        Vote vote = voteRepository.findOneByUid(Objects.requireNonNull(voteUid));
        logger.debug("Fetching vote results, for vote: {}, elapsed so far: {} msecs", vote.getName(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

        try {
            validateUserPartOfVote(user, vote, false);
            long sizeOfVote = vote.isAllGroupMembersAssigned() ? membershipRepository.countByGroup(vote.getAncestorGroup()) :
                    vote.getAssignedMembers().size();
            logger.debug("And now have group size, elapsed: {} msecs", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            Map<String, Long> resultMap = StringArrayUtil.isAllEmptyOrNull(vote.getVoteOptions()) ?
                    calculateYesNoResults(vote) : calculateMultiOptionResults(vote, vote.getVoteOptions());
            logger.info("Calculation done, elapsed: {} msecs", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            resultMap.put("TOTAL_VOTE_MEMBERS", sizeOfVote);
            return resultMap;
        } catch (AccessDeniedException e) {
            if (swallowMemberException) {
                return new HashMap<>();
            } else {
                throw e;
            }
        }
    }

    @Override
    public Optional<Vote> getMassVoteOpenForGroup(final Group group) {
        return voteRepository.findAll(EventSpecifications.isOpenMassVoteForGroup(group), SORT_DIRECTION).stream().findFirst();
    }

    @Override
    public Optional<Vote> getNextMassVoteForGroup(final Group group, int votePlaceInQueue) {
        final int pageSize = Math.min(1, votePlaceInQueue - 1);
        logger.info("Looking for subsequent mass vote, passed place: {}, floored: {}", votePlaceInQueue, pageSize);
        final PageRequest pageRequest = PageRequest.of(1, pageSize, SORT_DIRECTION);
        return voteRepository.findAll(EventSpecifications.isOpenMassVoteForGroup(group), pageRequest).stream().findFirst();
    }

    @Override
    @Transactional
    public void updateVoteOptions(String userUid, String voteUid, List<String> voteOptions, Map<Locale, List<String>> multiLingualVoteOptions) {
        final User user = userService.load(userUid);
        final Vote vote = voteRepository.findOneByUid(voteUid);
        validateUserCanEdit(vote, user);

        if (!CollectionUtils.isEmpty(voteOptions)) {
            vote.setVoteOptions(voteOptions);
        }

        if (!MapUtils.isEmpty(multiLingualVoteOptions)) {
            vote.setMultiLangOptions(multiLingualVoteOptions);
        }

        eventLogRepository.save(new EventLog(user, vote, CHANGE, "Altered vote options"));
    }

    @Override
    @Transactional
    public void updateMassVotePrompts(String userUid, String voteUid, Map<Locale, String> updatedPrompts, Map<Locale, String> updatedPostVote) {
        final User user = userService.load(userUid);
        final Vote vote = voteRepository.findOneByUid(voteUid);
        validateUserCanEdit(vote, user);

        if (updatedPrompts != null && !updatedPrompts.isEmpty()) {
            vote.setLangaugePrompts(updatedPrompts);
        }

        if (updatedPostVote != null && !updatedPostVote.isEmpty()) {
            vote.setPostVotePrompts(updatedPostVote);
        }

        eventLogRepository.save(new EventLog(user, vote, CHANGE, "Altered opening and post vote prompts"));
    }

    @Override
    @Transactional
    public void updateMassVoteClearPostVote(String userUid, String voteUid) {
        final User user = userService.load(userUid);
        final Vote vote = voteRepository.findOneByUid(voteUid);
        validateUserCanEdit(vote, user);

        vote.removePostVotePrompts();
        logger.info("Removed post vote prompts, now vote tags = {}", vote.getTagList());
        eventLogRepository.save(new EventLog(user, vote, CHANGE, "Remove post vote prompts"));
    }

    @Override
    @Transactional
    public void updateMassVoteToggles(String userUid, String voteUid, Boolean randomizeOptions, Boolean preCloseVote) {
        final User user = userService.load(userUid);
        final Vote vote = voteRepository.findOneByUid(voteUid);
        validateUserCanEdit(vote, user);

        String logDescription = "Changed ";
        if (randomizeOptions != null) {
            vote.setRandomize(randomizeOptions);
            logDescription += "randomize to " + randomizeOptions + " ";
        }

        if (preCloseVote != null) {
            vote.setPreClosed(preCloseVote);
            logDescription += "preclosed to " + preCloseVote;
        }

        eventLogRepository.save(new EventLog(user, vote, CHANGE, logDescription));
    }

    private void validateUserCanEdit(Vote vote, User user) {
        if (!vote.getCreatedByUser().equals(user))
            throw new AccessDeniedException("Error! Only creating user can perform these edits");
    }

    private Map<String, Long> calculateMultiOptionResults(Vote vote, List<String> options) {
        Map<String, Long> results = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();
//        List<EventLog> eventLogs = eventLogRepository.findAll(Specification.where(isResponseToVote(vote)));
        options.forEach(o -> results.put(o, eventLogRepository.count(isVoteOptionSelection(vote, o))));
        logger.info("Result count took {} msecs", System.currentTimeMillis() - startTime);
        return results;
    }

    private Map<String, Long> calculateYesNoResults(Vote vote) {
        // vote may have been done via old method, so need to do a check, for now, if there are no
        // option responses (note: if no responses at all, it will still return valid result, since we
        // know at this point that it is a yes/no vote)

        return eventLogRepository.count(Specification.where(ofType(EventLogType.VOTE_OPTION_RESPONSE))) == 0 ?
                calculateOldVoteResult(vote) :
                calculateMultiOptionResults(vote, optionsForYesNoVote); // will just return
    }

    private Map<String, Long> calculateOldVoteResult(Vote vote) {

        List<EventLog> eventLogs = eventLogRepository.findAll(Specification.where(
                ofType(EventLogType.RSVP)).and(forEvent(vote)));
        Map<String, Long> results = new LinkedHashMap<>();
        results.put(YES, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.YES)).count());
        results.put(NO, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.NO)).count());
        results.put(ABSTAIN, eventLogs.stream().filter(el -> el.getResponse().equals(EventRSVPResponse.MAYBE)).count());
        return results;
    }

    private void validateVoteClosingTime(Instant closingTime) {
        Instant now = Instant.now();
        if (!closingTime.isAfter(now)) {
            throw new EventStartTimeNotInFutureException("Event start time " + closingTime.toString() +
                    " is not in the future");
        }
    }

    // not yet checking if vote has already been cast, since we allow for vote revision
    // (at the moment), and hence just check start date and part of vote members
    private void validateUserPartOfVote(User user, Vote vote, boolean checkVoteOpen) {
        if (checkVoteOpen && vote.getEventStartDateTime().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Error! Cannot respond to a closed vote");
        }

        boolean isUserInGroup = vote.isAllGroupMembersAssigned() ?
                user.isMemberOf(vote.getThisOrAncestorGroup()) :
                vote.getAssignedMembers().contains(user);

        if (!isUserInGroup) {
            throw new AccessDeniedException("Only users part of group or assigned to vote can view or respond to vote");
        }
    }
}
