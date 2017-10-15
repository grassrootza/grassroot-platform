package za.org.grassroot.webapp.controller.rest.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.services.group.GroupBroker;

@Controller
public class GroupBaseController {

    protected GroupBroker groupBroker;

    @Autowired
    protected void setGroupBroker(GroupBroker groupBroker) {
        this.groupBroker = groupBroker;
    }

}
