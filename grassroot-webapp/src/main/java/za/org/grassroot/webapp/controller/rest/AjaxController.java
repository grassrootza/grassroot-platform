package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.MemberListDTO;
import za.org.grassroot.webapp.model.rest.wrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * Created by luke on 2016/04/28.
 */
@RestController @Slf4j
@RequestMapping(value = "/ajax")
public class AjaxController extends BaseController {

    private GeoLocationBroker geoLocationBroker;

    public AjaxController(UserManagementService userManagementService, PermissionBroker permissionBroker, GeoLocationBroker geoLocationBroker) {
        super(userManagementService, permissionBroker);
        this.geoLocationBroker = geoLocationBroker;
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

}
