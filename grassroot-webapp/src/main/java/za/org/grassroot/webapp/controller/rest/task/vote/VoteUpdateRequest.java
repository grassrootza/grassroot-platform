package za.org.grassroot.webapp.controller.rest.task.vote;

import lombok.Value;

import java.util.Locale;
import java.util.Map;

@Value
public class VoteUpdateRequest {

    private Map<Locale, String> multiLanguagePrompts;
    private Map<Locale, String> postVotePrompts;

}
