package za.org.grassroot.webapp.controller.rest.task.vote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventSpecialForm;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter @JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VoteDetailsResponse {

    private EventSpecialForm specialForm;
    private boolean sendNotifications;
    private boolean excludeAbstain;
    private Map<Locale, String> multiLanguagePrompts;
    private Map<Locale, String> postVotePrompts;

    public VoteDetailsResponse(final Vote vote) {
        this.specialForm = vote.getSpecialForm();
        this.sendNotifications = !vote.shouldStopNotifications();
        this.excludeAbstain = vote.shouldExcludeAbstention();
        this.multiLanguagePrompts = !vote.hasAdditionalLanguagePrompts() ? new HashMap<>() :
                vote.getPromptLanguages().stream().collect(Collectors.toMap(lang -> lang, lang -> vote.getLanguagePrompt(lang).orElse(vote.getName())));
        this.postVotePrompts = !vote.hasPostVotePrompt() ? new HashMap<>() :
                vote.getPromptLanguages().stream().collect(Collectors.toMap(lang -> lang, lang -> vote.getPostVotePrompt(lang).orElse("")));
    }

}
