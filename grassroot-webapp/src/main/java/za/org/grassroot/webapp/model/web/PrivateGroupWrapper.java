package za.org.grassroot.webapp.model.web;

import za.org.grassroot.core.domain.Group;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by luke on 2016/11/29.
 */
public class PrivateGroupWrapper {

    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");

    private final String uid;
    private final String name;
    private final String description;

    private final String creatorName;
    private final int memberCount;
    private final LocalDateTime createdDateTime;
    private final String formattedCreationTime;

    public PrivateGroupWrapper(Group group) {
        this.uid = group.getUid();
        this.name = group.getName();
        this.description = group.getDescription();

        this.memberCount = group.getMemberships().size();
        this.createdDateTime = group.getCreatedDateTimeAtSAST().toLocalDateTime();
        this.formattedCreationTime = format.format(createdDateTime);

        this.creatorName = group.getCreatedByUser().getDisplayName();
    }

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public LocalDateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public String getFormattedCreationTime() {
        return formattedCreationTime;
    }

    public String getCreatorName() {
        return creatorName;
    }
}
