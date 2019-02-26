package za.org.grassroot.core.dto.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.membership.MembershipDTO;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApiModel @Getter @Slf4j
public class GroupFullDTO extends GroupMinimalDTO {

    // note: in future we may make this variable / settable
    private static final int MAX_JOIN_WORDS = 3;

    private final String joinCode;
    private final boolean paidFor;
    private final String groupCreatorName;
    private final String groupCreatorUid;
    private final Long groupCreationTimeMillis;
    private final Integer reminderMinutes;

    @Setter private Set<MembershipDTO> members;
    @Setter private List<MembershipRecordDTO> memberHistory;
    @Setter private List<GroupMembersDTO> subGroups = new ArrayList<>();
    @Setter private List<String> topics = new ArrayList<>();
    @Setter private List<String> joinTopics = new ArrayList<>();
    @Setter private Set<String> affiliations = new HashSet<>();
    @Setter private List<JoinWordDTO> joinWords = new ArrayList<>();
    @Setter private int joinWordsLeft;
    @Setter private boolean hasInboundMessages;
    @Setter private String joinMessage;

    public GroupFullDTO(Group group, Membership membership, MembershipRepository membershipRepository) {
        super(group, membership, membershipRepository);
        this.groupCreatorUid = group.getCreatedByUser().getUid();
        this.groupCreatorName = group.getCreatedByUser().getName();
        this.groupCreationTimeMillis = group.getCreatedDateTime().toEpochMilli();

        this.joinCode = group.getGroupTokenCode();
        this.topics.addAll(group.getTopics());
        this.joinTopics.addAll(group.getJoinTopics());
        this.paidFor = group.isPaidFor();
        this.reminderMinutes = group.getReminderMinutes();
        this.joinWords = group.getActiveJoinCodes().stream()
                .map(w -> new JoinWordDTO(w.getCode(), w.getShortUrl()))
                .collect(Collectors.toList());

        this.joinWordsLeft = MAX_JOIN_WORDS - this.joinWords.size();

        final Set<String[]> tagsSet = membershipRepository.findDistinctMembershipTagsByGroup(group);
        this.affiliations = tagsSet.stream()
                .flatMap(tags -> Membership.extractAffiliations(Arrays.asList(tags)))
                .collect(Collectors.toSet());

        this.members = new HashSet<>();
        this.hasInboundMessages = false;
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
