package za.org.grassroot.webapp.model.web;

import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.format.DateTimeFormatter;

/**
 * Created by luke on 2016/09/22.
 */
public class PublicGroupWrapper {

    private final String groupUid;
    private final String groupName;
    private final String groupDescription;

    public PublicGroupWrapper(Group group, String descriptionFormat) {
        this.groupUid = group.getUid();
        this.groupName = group.getName();

        if (!StringUtils.isEmpty(group.getDescription())) {
            this.groupDescription = group.getDescription();
        } else {
            final String dateEst = DateTimeFormatter.ofPattern("d MMM yyyy").format(group.getCreatedDateTimeAtSAST());
            this.groupDescription = String.format(descriptionFormat,
                    dateEst, group.getCreatedByUser().nameToDisplay(), group.getMemberships().size());
        }
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getGroupDescription() {
        return groupDescription;
    }
}
