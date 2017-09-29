package za.org.grassroot.webapp.controller.rest.request;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@Api("/api/request/fetch")
@RequestMapping(value = "/api/request/fetch")
public class RequestFetchController {

    private static final Logger logger = LoggerFactory.getLogger(RequestFetchController.class);

    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public RequestFetchController(GroupJoinRequestService groupJoinRequestService) {
        this.groupJoinRequestService = groupJoinRequestService;
    }

    @RequestMapping(value = "/group/{userUid}/{groupUid}")
    public ResponseEntity<ResponseWrapper> fetchGroupJoinRequests(@PathVariable String userUid,
                                                                  @PathVariable String groupUid) {
        return RestUtil.okayResponseWithData(RestMessage.JOIN_REQUESTS,
                groupJoinRequestService.getPendingRequestsForGroup(userUid, groupUid));
    }


}
