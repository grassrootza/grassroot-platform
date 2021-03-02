package za.org.grassroot.webapp.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.webapp.controller.rest.exception.FileCreationException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.wrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.PermissionLackingWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.*;

@Slf4j
public class RestUtil {

    private final static Set<Permission> homeScreenPermissions = Stream.of(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
                                                                   Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
                                                                   Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
                                                                   Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
                                                                   Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
                                                                   Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
                                                                   Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER).collect(Collectors.toSet());

    public static ResponseEntity<byte[]> convertWorkbookToDownload(String fileName, XSSFWorkbook xls) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xls.write(baos);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("IO Exception generating spreadsheet!", e);
            throw new FileCreationException();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
    }

    public static ResponseEntity<byte[]> convertWorkbookCSVToDownload(String fileName, File csvFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/csv"));
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
            return new ResponseEntity<>(Files.readAllBytes(csvFile.toPath()), headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("IO Exception generating csv!", e);
            throw new FileCreationException();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
    }

    public static Set<Permission> filterPermissions(Set<Permission> permissions){
        return permissions.stream().filter(homeScreenPermissions::contains)
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

    public static ResponseEntity<ResponseWrapper> errorResponse(RestMessage restMessage) {
        return errorResponse(HttpStatus.BAD_REQUEST, restMessage);
    }

    public static ResponseEntity<ResponseWrapper> accessDeniedResponse() {
        return new ResponseEntity<>(new ResponseWrapperImpl(FORBIDDEN, RestMessage.PERMISSION_DENIED, RestStatus.FAILURE), FORBIDDEN);
    }

    public static ResponseEntity<ResponseWrapper> lackPermissionResponse(Permission permission) {
        return new ResponseEntity<>(new PermissionLackingWrapper(FORBIDDEN, permission, RestStatus.FAILURE), FORBIDDEN);
    }

    public static ResponseEntity<ResponseWrapper> messageOkayResponse(RestMessage message) {
        return new ResponseEntity<>(new ResponseWrapperImpl(OK, message, RestStatus.SUCCESS), OK);
    }

    public static ResponseEntity<ResponseWrapper> okayResponseWithData(RestMessage message, Object data) {
        return new ResponseEntity<>(new GenericResponseWrapper(OK, message, RestStatus.SUCCESS, data), OK);
    }

    public static ResponseEntity<ResponseWrapper> errorResponseWithData(RestMessage message, Object data) {
        GenericResponseWrapper error = new GenericResponseWrapper(BAD_REQUEST, message, RestStatus.FAILURE, data);
        return new ResponseEntity<>(error, BAD_REQUEST);
    }
}
