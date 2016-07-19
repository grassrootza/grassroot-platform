package za.org.grassroot.webapp.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

/**
 * Created by paballo on 2016/03/22.
 */
public class RestUtil {

    final static Set<Permission> homeScreenPermissions = Stream.of(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
                                                                   Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
                                                                   Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
                                                                   Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
                                                                   Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                                                                   Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                                                                   Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER).collect(Collectors.toSet());

    public static Set<Permission> filterPermissions(Set<Permission> permissions){
        return permissions.stream().filter(p -> homeScreenPermissions.contains(p))
                .collect(Collectors.toSet());
    }

    public static int getReminderMinutes(int option) {
        int reminderMins;
        switch (option) {
            case '0':
                reminderMins = 5;
                break;
            case '1':
                reminderMins = 60;
                break;
            case '2':
                reminderMins = 60 * 12;
                break;
            case '3':
                reminderMins = 60 * 24;
                break;
            default:
                reminderMins = 5;
                break;
        }
        return reminderMins;
    }

	public static ResponseEntity<ResponseWrapper> errorResponse(HttpStatus httpCode, RestMessage message) {
		return new ResponseEntity<>(new ResponseWrapperImpl(httpCode, message, RestStatus.FAILURE), httpCode);
	}

    public static ResponseEntity<ResponseWrapper> accessDeniedResponse() {
	    return new ResponseEntity<>(new ResponseWrapperImpl(FORBIDDEN, RestMessage.PERMISSION_DENIED, RestStatus.FAILURE), FORBIDDEN);
    }

	public static ResponseEntity<ResponseWrapper> messageOkayResponse(RestMessage message) {
		return new ResponseEntity<>(new ResponseWrapperImpl(OK, message, RestStatus.SUCCESS), OK);
	}

	public static ResponseEntity<ResponseWrapper> okayResponseWithData(RestMessage message, Object data) {
		return new ResponseEntity<>(new GenericResponseWrapper(OK, message, RestStatus.SUCCESS, data), OK);
	}

}

