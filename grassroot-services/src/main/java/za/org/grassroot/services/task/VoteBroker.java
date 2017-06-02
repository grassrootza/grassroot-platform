package za.org.grassroot.services.task;

import java.util.List;

/**
 * Created by luke on 2017/05/31.
 */
public interface VoteBroker {

    void addVoteOption(String userUid, String voteUid, String voteOption);

    // note: pass in an empty list to revert to yes/no
    void setListOfOptions(String userUid, String voteUid, List<String> options);

    void recordUserVote(String userUid, String voteUid, String voteOption);

    void calculateAndSendVoteResults(String voteUid);

}
