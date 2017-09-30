package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.MemberListDTO;
import za.org.grassroot.webapp.model.rest.wrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.model.web.AutoCompleteResponse;
import za.org.grassroot.webapp.model.web.MemberPicker;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/04/28.
 */
@RestController
@RequestMapping(value = "/ajax")
public class AjaxController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AjaxController.class);

    private static final String UID_VALUE = "UID";
    private static final String NAME_VALUE = "NAME";
    private static final String PRIVATE_SEARCH = "PRIVATE";
    private static final String PUBLIC_SEARCH = "PUBLIC";

    private GroupQueryBroker groupQueryBroker;
    private EventBroker eventBroker;
    private TodoBroker todoBroker;
    private TaskBroker taskBroker;
    private GeoLocationBroker geoLocationBroker;

    @Autowired
    public AjaxController(GroupQueryBroker groupQueryBroker, EventBroker eventBroker, TodoBroker todoBroker,
                          TaskBroker taskBroker, GeoLocationBroker geoLocationBroker) {
        this.groupQueryBroker = groupQueryBroker;
        this.eventBroker = eventBroker;
        this.todoBroker = todoBroker;
        this.taskBroker = taskBroker;
        this.geoLocationBroker = geoLocationBroker;
    }

    @RequestMapping(value = "/members/list", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> retrieveParentMembers(@RequestParam JpaEntityType parentEntityType,
                                                                 @RequestParam String parentUid,
                                                                 @RequestParam boolean selectedByDefault) {

        MemberPicker memberPicker;

        if (JpaEntityType.GROUP.equals(parentEntityType)) {
            memberPicker = new MemberPicker(groupQueryBroker.load(parentUid), selectedByDefault);
        } else if (JpaEntityType.MEETING.equals(parentEntityType) || JpaEntityType.VOTE.equals(parentEntityType)) {
            memberPicker = new MemberPicker(eventBroker.load(parentUid), selectedByDefault);
        } else if (JpaEntityType.TODO.equals(parentEntityType)) {
            memberPicker = new MemberPicker(todoBroker.load(parentUid), selectedByDefault);
        } else {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_ENTITY_TYPE);
        }

        ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS,
                RestStatus.SUCCESS, memberPicker);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @RequestMapping(value = "/tasks/fetch", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchTaskDescription(@RequestParam String taskUid,
                                                                @RequestParam TaskType taskType) {
        log.info("Fetching task description for taskUid={}", taskUid);
        try {
            TaskDTO taskDTO = taskBroker.load(getUserProfile().getUid(), taskUid, taskType);
            ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.TASK_FOUND, RestStatus.SUCCESS, taskDTO);
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (Exception e) {
            log.info("Error fetching task! Error: {}", e.toString());
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_INPUT);
        }
    }

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> logIncomingRequest(@RequestBody MemberListDTO listRequest, HttpServletRequest request) {
        log.info("Received a request! It's this: {}", listRequest.toString());
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS, RestStatus.SUCCESS),
                                    HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/locations/list", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> retrieveLocationLogs(@RequestParam String userUid) {
        log.info("Retrieving raw location logs for = {}", userUid);
        List<double[]> listLatLongs = geoLocationBroker.fetchUserLatitudeLongitudeInAvgPeriod(userUid, LocalDate.now());
        ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.TASK_FOUND, RestStatus.SUCCESS, listLatLongs);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @RequestMapping(value = "/user/names", method = RequestMethod.GET)
    public @ResponseBody List<AutoCompleteResponse> retrieveUserGraphNames(@RequestParam String fragment) {
        return userManagementService.findOthersInGraph(getUserProfile(), fragment)
                .stream()
                .map(s -> new AutoCompleteResponse(PhoneNumberUtil.invertPhoneNumber(s[1]), s[0])) // phoneNumber as value, name as label
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/group/names", method = RequestMethod.GET)
    public @ResponseBody List<AutoCompleteResponse> retrieverUserGroupNames(@RequestParam String fragment,
                                                                            @RequestParam String valueType,
                                                                            @RequestParam String searchType) {
        boolean returnUid = UID_VALUE.equals(valueType);
        return groupQueryBroker.groupSearch(getUserProfile().getUid(), fragment, PUBLIC_SEARCH.equals(searchType))
                .stream()
                .map(g -> new AutoCompleteResponse(returnUid ? g.getUid() : g.getName(), g.getName()))
                .collect(Collectors.toList());
    }

}
