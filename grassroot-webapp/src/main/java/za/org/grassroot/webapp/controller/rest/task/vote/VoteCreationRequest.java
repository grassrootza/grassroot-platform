package za.org.grassroot.webapp.controller.rest.task.vote;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.task.VoteHelper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor @Setter @Getter @ToString
public class VoteCreationRequest {

    String title;
    List<String> voteOptions;
    String description;
    long time;
    EventSpecialForm specialForm;
    Boolean randomizeOptions;
    String mediaFileUid;
    Set<String> assignedMemberUids;

    Map<Locale, String> multiLanguagePrompts;
    Map<Locale, String> postVotePrompts;
    Map<Locale, List<String>> multiLanguageOptions;

    boolean sendNotifications = true;
    boolean excludeAbstain = false;

    public VoteHelper convertToHelper(final String userUid, final String parentUid, final JpaEntityType parentType) {

        LocalDateTime eventStartDateTime = Instant.ofEpochMilli(time).atZone(DateTimeUtil.getSAST()).toLocalDateTime();

        return VoteHelper.builder()
                .userUid(userUid)
                .parentUid(parentUid)
                .parentType(parentType)
                .name(title)
                .eventStartDateTime(eventStartDateTime)
                .description(description)
                .options(voteOptions)
                .taskImageKey(mediaFileUid)
                .assignMemberUids(assignedMemberUids == null ? new HashSet<>() : assignedMemberUids)
                .specialForm(specialForm)
                .randomizeOptions(randomizeOptions != null && randomizeOptions)
                .multiLanguagePrompts(multiLanguagePrompts)
                .postVotePrompts(postVotePrompts)
                .multiLanguageOptions(multiLanguageOptions)
                .sendNotifications(sendNotifications)
                .excludeAbstain(excludeAbstain)
                .build();

    }

}
