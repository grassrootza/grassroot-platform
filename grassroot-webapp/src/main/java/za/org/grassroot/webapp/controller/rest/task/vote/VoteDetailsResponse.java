package za.org.grassroot.webapp.controller.rest.task.vote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventSpecialForm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter @JsonInclude(JsonInclude.Include.NON_EMPTY) @Slf4j
public class VoteDetailsResponse {

    private EventSpecialForm specialForm;
    private boolean sendNotifications;
    private boolean excludeAbstain;
    private boolean randomizeOptions;
    private boolean preClosed;

    private Map<Locale, String> multiLanguagePrompts;
    private Map<Locale, String> postVotePrompts;
    private Map<Locale, List<String>> multiLanguageOptions;

    public VoteDetailsResponse(final Vote vote) {
        this.specialForm = vote.getSpecialForm();
        this.sendNotifications = !vote.shouldStopNotifications();
        this.excludeAbstain = vote.shouldExcludeAbstention();
        this.randomizeOptions = vote.shouldRandomize();
        this.preClosed = vote.isPreClosed();

        this.multiLanguagePrompts = !vote.hasAdditionalLanguagePrompts() ? new HashMap<>() :
                vote.getPromptLanguages().stream().collect(Collectors.toMap(lang -> lang, lang -> vote.getLanguagePrompt(lang).orElse(vote.getName())));
        log.info("Does this have a prompt? : {}, and languages : {}", vote.hasPostVotePrompt(), vote.getPostVoteLanguages());
        this.postVotePrompts = !vote.hasPostVotePrompt() ? new HashMap<>() :
                vote.getPostVoteLanguages().stream().collect(Collectors.toMap(lang -> lang, lang -> vote.getPostVotePrompt(lang).orElse("")));
        log.info("Retrieved post vote prompts: {}", this.postVotePrompts);
        this.multiLanguageOptions = vote.getMultiLangOptions();
    }

}
