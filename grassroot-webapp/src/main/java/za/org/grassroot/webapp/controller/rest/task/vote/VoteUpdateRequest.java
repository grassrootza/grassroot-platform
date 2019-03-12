package za.org.grassroot.webapp.controller.rest.task.vote;

import lombok.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
public class VoteUpdateRequest {

    private Long voteClosingDateMillis;
    private List<String> voteOptions;
    private Map<Locale, String> multiLanguagePrompts;
    private Map<Locale, String> postVotePrompts;
    private Map<Locale, List<String>> multiLingualOptions;
    private Boolean randomizeOptions;
    private Boolean preCloseVote;

}
