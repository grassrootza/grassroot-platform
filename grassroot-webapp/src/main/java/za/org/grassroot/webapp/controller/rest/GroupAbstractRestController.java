package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupQueryBroker;
import za.org.grassroot.webapp.model.rest.wrappers.GroupResponseWrapper;

/**
 * Created by luke on 2016/09/28.
 */
@Controller
public class GroupAbstractRestController {

    @Autowired
    protected EventBroker eventBroker;

    @Autowired
    protected GroupBroker groupBroker;

    @Autowired
    protected GroupQueryBroker groupQueryBroker;

    protected GroupResponseWrapper createGroupWrapper(Group group, User caller) {
        Role role = group.getMembership(caller).getRole();
        Event event = eventBroker.getMostRecentEvent(group.getUid());
        GroupLog groupLog = groupQueryBroker.getMostRecentLog(group);

        boolean hasTask = event != null;
        GroupResponseWrapper responseWrapper;
        if (hasTask && event.getEventStartDateTime().isAfter(groupLog.getCreatedDateTime())) {
            responseWrapper = new GroupResponseWrapper(group, event, role, true);
        } else {
            responseWrapper = new GroupResponseWrapper(group, groupLog, role, hasTask);
        }
        // log.info("created response wrapper = {}", responseWrapper);
        return responseWrapper;
    }

}
