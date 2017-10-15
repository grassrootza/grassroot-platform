package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.Vote;

import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2017/05/31.
 */
public interface VoteBroker {

    Vote load(String voteUid);

    void addVoteOption(String userUid, String voteUid, String voteOption);

    // note: pass in an empty list to revert to yes/no
    void setListOfOptions(String userUid, String voteUid, List<String> options);

    void recordUserVote(String userUid, String voteUid, String voteOption);

    void calculateAndSendVoteResults(String voteUid);

    Map<String, Long> fetchVoteResults(String userUid, String voteUid, boolean swallowMemberException);

}