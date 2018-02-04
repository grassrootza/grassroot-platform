package za.org.grassroot.core.dto.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipDTO;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApiModel @Getter @Slf4j
public class GroupFullDTO extends GroupHeavyDTO {

    // note: in future we may make this variable / settable
    private static final int MAX_JOIN_WORDS = 3;

    private final String joinCode;
    private final boolean paidFor;
    private final Set<MembershipDTO> members;
    @Setter private List<MembershipRecordDTO> memberHistory;
    @Setter private List<GroupMembersDTO> subGroups = new ArrayList<>();
    @Setter private List<String> topics = new ArrayList<>();
    @Setter private List<String> affiliations = new ArrayList<>();
    @Setter private List<JoinWordDTO> joinWords = new ArrayList<>();
    @Setter private int joinWordsLeft;
    private final Integer reminderMinutes;

    public GroupFullDTO(Group group, Membership membership) {
        super(group, membership);
        this.joinCode = group.getGroupTokenCode();
        this.topics.addAll(group.getTopics());
        this.paidFor = group.isPaidFor();
        this.reminderMinutes = group.getReminderMinutes();
        this.joinWords.addAll(group.getGroupJoinCodes().stream()
                .filter(GroupJoinCode::isActive)
                .filter(g -> JoinCodeType.JOIN_WORD.equals(g.getType()))
                .map(w -> new JoinWordDTO(w.getCode(), w.getShortUrl()))
                .collect(Collectors.toList()));

        this.joinWordsLeft = MAX_JOIN_WORDS - this.joinWords.size();
        this.affiliations = group.getMemberships().stream().flatMap(m -> m.getAffiliations().stream())
                .distinct().collect(Collectors.toList());

        if (membership.getRole().getPermissions().contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            this.members = group.getMemberships().stream()
                .map(MembershipDTO::new).collect(Collectors.toSet());
        } else {
            this.members = new HashSet<>();
        }
    }

    /*
    Where group description is empty, introduce a default, using a template, that must call, in order:
    1 - Group name
    2 - Its creation date
    3 - How many members it has
    4 - What its join code is
     */
    public GroupFullDTO insertDefaultDescriptionIfEmpty(String descriptionTemplate) {
        if (StringUtils.isEmpty(description)) {
            final String createdDateTime = DateTimeUtil.getPreferredDateFormat().format(
                    DateTimeUtil.convertToUserTimeZone(Instant.ofEpochMilli(groupCreationTimeMillis), DateTimeUtil.getSAST()));
            this.description = String.format(descriptionTemplate, getName(), createdDateTime, memberCount, joinCode);
        }
        return this;
    }

}
