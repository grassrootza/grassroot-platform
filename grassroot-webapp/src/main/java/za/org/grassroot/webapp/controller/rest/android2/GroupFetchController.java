package za.org.grassroot.webapp.controller.rest.android2;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(value = "/api/mobile/group/fetch")
public class GroupFetchController {

    private static final Logger logger = LoggerFactory.getLogger(GroupFetchController.class);

    private final GroupFetchBroker groupFetchBroker;

    @Autowired
    public GroupFetchController(GroupFetchBroker groupFetchBroker) {
        this.groupFetchBroker = groupFetchBroker;
    }

    /**
     * Tells the client which, if any, of the groups have had a structural change since the last time the client knew about
     * Note: does not include / check for a more recent task time (on grounds if client wants to / needs to know that, there
     * are other methods and/or it can store or fetch the tasks itself)
     * @param userUid The UID of the user calling this group
     * @param existingGroups The groups that already exist on the client, with the epochMilli time they were last updated
     * @return
     */
    @Timed
    @RequestMapping(value = "/updated/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> fetchUpdatedGroups(@PathVariable String userUid,
                                                              @RequestBody Map<String, Long> existingGroups) {
        logger.info("checking for groups changes, map sent in: {}", existingGroups);
        return RestUtil.okayResponseWithData(RestMessage.USER_GROUPS,
                groupFetchBroker.findNewlyChangedGroups(userUid, existingGroups));
    }

    /**
     * Sends back a minimal set of information about the groups: names, size, last activity times
     * @param userUid
     * @param groupUids
     * @return
     */
    @RequestMapping(value = "/info/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchGroupInfo(@PathVariable String userUid,
                                                          @RequestParam(required = false) Set<String> groupUids) {
        return RestUtil.okayResponseWithData(RestMessage.USER_GROUPS,
                groupFetchBroker.fetchGroupMinimalInfo(userUid, groupUids));
    }

    /**
     * Full, heavy group retrieval--gets everything a client likely needs
     * @param userUid
     * @param groupUids
     * @return
     */
    @RequestMapping(value = "/full/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchFullGroupInfo(@PathVariable String userUid,
                                                              @RequestParam(required = false) Set<String> groupUids) {
        return RestUtil.okayResponseWithData(RestMessage.USER_GROUPS,
                groupFetchBroker.fetchGroupFullInfo(userUid, groupUids));
    }
}
