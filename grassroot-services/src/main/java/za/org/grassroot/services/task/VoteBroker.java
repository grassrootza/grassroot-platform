package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Vote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Created by luke on 2017/05/31.
 */
public interface VoteBroker {

    Vote load(String voteUid);

    // votes cannot change topic or scope (groups included or not) after creation, just closing time & description field
    Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description);

    void recordUserVote(String userUid, String voteUid, String voteOption);

    void calculateAndSendVoteResults(String voteUid);

    Map<String, Long> fetchVoteResults(String userUid, String voteUid, boolean swallowMemberException);

    Optional<Vote> getMassVoteOpenForGroup(Group group);

    Optional<Vote> getNextMassVoteForGroup(Group group, int votePlaceInQueue);

    void updateVoteOptions(String userUid, String voteUid, List<String> voteOptions, Map<Locale, List<String>> multiLingualVoteOptions);

    void updateMassVotePrompts(String userUid, String voteUid, Map<Locale, String> updatedPrompts, Map<Locale, String> updatedPostVote);

    void updateMassVoteClearPostVote(String userUid, String voteUid);

    void updateMassVoteToggles(String userUid, String voteUid, Boolean randomizeOptions, Boolean preCloseVote, Boolean noChangeVote);

    boolean hasUserVoted(User user, Vote vote);

}