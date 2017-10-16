package za.org.grassroot.webapp.controller.android1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.model.rest.wrappers.GroupResponseWrapper;

/**
 * Created by luke on 2016/09/28.
 */
@Controller
public class GroupAbstractRestController {

    @Autowired
    protected UserManagementService userManagementService;

    @Autowired
    protected EventBroker eventBroker;

    @Autowired
    protected GroupBroker groupBroker;

    @Autowired
    protected GroupQueryBroker groupQueryBroker;

    @Autowired
    protected PermissionBroker permissionBroker;

    protected GroupResponseWrapper createGroupWrapper(Group group, User caller) {
        Role role = group.getMembership(caller).getRole();
        Event event = eventBroker.getMostRecentEvent(group.getUid());
        GroupLog groupLog = groupQueryBroker.getMostRecentLog(group);
        boolean hasTask = event != null;

        return hasTask && event.getEventStartDateTime().isAfter(groupLog.getCreatedDateTime()) ?
                new GroupResponseWrapper(group, event, role, true) : new GroupResponseWrapper(group, groupLog, role, hasTask);
    }

}
