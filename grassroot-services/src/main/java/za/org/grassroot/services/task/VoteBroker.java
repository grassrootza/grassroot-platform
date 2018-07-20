package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.Vote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2017/05/31.
 */
public interface VoteBroker {

    Vote load(String voteUid);

    // votes cannot change topic or scope (groups included or not) after creation, just closing time & description field
    Vote updateVote(String userUid, String voteUid, LocalDateTime eventStartDateTime, String description);

    void updateVoteClosingTime(String userUid, String eventUid, LocalDateTime closingDateTime);

    void addVoteOption(String userUid, String voteUid, String voteOption);

    // note: pass in an empty list to revert to yes/no
    void setListOfOptions(String userUid, String voteUid, List<String> options);

    void recordUserVote(String userUid, String voteUid, String voteOption);

    void calculateAndSendVoteResults(String voteUid);

    Map<String, Long> fetchVoteResults(String userUid, String voteUid, boolean swallowMemberException);

    boolean hasMassVoteOpen(String groupUid);

    Vote getMassVoteForGroup(String groupUid);

}