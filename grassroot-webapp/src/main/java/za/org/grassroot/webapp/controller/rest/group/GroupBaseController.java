package za.org.grassroot.webapp.controller.rest.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;

@Controller
public class GroupBaseController extends BaseRestController {

    protected GroupBroker groupBroker;

    public GroupBaseController(JwtService jwtService, UserManagementService userManagementService) {
        super(jwtService, userManagementService);
    }

    @Autowired
    protected void setGroupBroker(GroupBroker groupBroker) {
        this.groupBroker = groupBroker;
    }

}
