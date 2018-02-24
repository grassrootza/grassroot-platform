package za.org.grassroot.webapp.controller.rest.search;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.group.GroupWebDTO;
import za.org.grassroot.core.dto.task.TaskDTO;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.task.TaskFetchController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api("/api/search")
@RequestMapping(value = "/api/search")
public class SearchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(TaskFetchController.class);

    private final TaskBroker taskBroker;
    private final GroupQueryBroker groupQueryBroker;

    @Autowired
    public SearchController(TaskBroker taskBroker,
                            GroupQueryBroker groupQueryBroker,
                            JwtService jwtService,
                            UserManagementService userManagementService){
        super(jwtService, userManagementService);
        this.taskBroker = taskBroker;
        this.groupQueryBroker = groupQueryBroker;
    }

    @Timed
    @RequestMapping(value = "/userTasks/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User tasks using search term")
    public ResponseEntity<List<TaskDTO>> searchForUserTasksByTerm(@PathVariable String userUid,
                                                                  @PathVariable String searchTerm,
                                                                  HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            List<TaskDTO> tasks = taskBroker.searchForTasks(userUid,searchTerm);
            logger.info("User tasks using search term{},tasks{}",searchTerm,tasks);
            return ResponseEntity.ok(tasks);
        }else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }


    @Timed
    @RequestMapping(value = "/groups/{userUid}/{searchTerm}", method = RequestMethod.GET)
    @ApiOperation(value = "User tasks using search term")
    public ResponseEntity<List<GroupWebDTO>> searchForUserGroupsByTerm(@PathVariable String userUid,
                                                                 @PathVariable String searchTerm,
                                                                 HttpServletRequest request){
        String loggedInUserUid = getUserIdFromRequest(request);
        if(userUid.equals(loggedInUserUid)){
            List<Group> groups = groupQueryBroker.searchUsersGroups(userUid,searchTerm,false);

            List<GroupWebDTO> dtos = groups.stream().map(gr -> new GroupWebDTO(gr, gr.getMembership(getUserFromRequest(request)), groupQueryBroker.getSubgroups(gr))).collect(Collectors.toList());

            logger.info("User groups using search term{},tasks{}",searchTerm,groups);
            return ResponseEntity.ok(dtos);
        }else{
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }
}