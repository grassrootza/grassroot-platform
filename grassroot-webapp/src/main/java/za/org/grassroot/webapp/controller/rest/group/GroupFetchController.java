package za.org.grassroot.webapp.controller.rest.group;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupWebDTO;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.exception.FileCreationException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Grassroot2RestController
@Slf4j
@Api("/api/group/fetch")
@RequestMapping(value = "/api/group/fetch")
public class GroupFetchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(GroupFetchController.class);

    private final GroupFetchBroker groupFetchBroker;
    private final PdfGeneratingService generatingService;
    private final MemberDataExportBroker memberDataExportBroker;


    public GroupFetchController(GroupFetchBroker groupFetchBroker, JwtService jwtService,
                                UserManagementService userManagementService, PdfGeneratingService generatingService,
                                MemberDataExportBroker memberDataExportBroker) {
        super(jwtService, userManagementService);
        this.groupFetchBroker = groupFetchBroker;
        this.generatingService = generatingService;
        this.memberDataExportBroker = memberDataExportBroker;
    }


    @ApiOperation("Returns a list of groups for currently logged in user")
    @RequestMapping(value = "/list")
    public ResponseEntity<List<GroupWebDTO>> listUserGroups(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId != null) {
            List<GroupWebDTO> groups = groupFetchBroker.fetchGroupWebInfo(userId);
            return new ResponseEntity<>(groups, HttpStatus.OK);
        } else return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
    }

    @RequestMapping(value = "/details/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<GroupFullDTO> loadFullGroupInfo(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        if (userUid != null) {
            final String descriptionTemplate = "Group '%1$s', created on %2$s, has %3$d members, with join code %4$s";
            GroupFullDTO dto = groupFetchBroker.fetchGroupFullDetails(userUid, groupUid).insertDefaultDescriptionIfEmpty(descriptionTemplate);
            return ResponseEntity.ok(dto);
        } else return new ResponseEntity<>((GroupFullDTO) null, HttpStatus.UNAUTHORIZED);

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
    public ResponseEntity<Set<GroupTimeChangedDTO>> fetchUpdatedGroups(@PathVariable String userUid,
                                                                       @RequestBody Map<String, Long> existingGroups) {
        logger.info("checking for groups changes, map sent in: {}", existingGroups);
        return ResponseEntity.ok(groupFetchBroker.findNewlyChangedGroups(userUid, existingGroups));
    }

    /**
     * Sends back a minimal set of information about the groups: names, size, last activity times
     * @param userUid
     * @param groupUids
     * @return
     */
    @RequestMapping(value = "/info/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<Set<GroupMinimalDTO>> fetchGroupInfo(@PathVariable String userUid,
                                                               @RequestParam(required = false) Set<String> groupUids) {
        return ResponseEntity.ok(groupFetchBroker.fetchGroupMinimalInfo(userUid, groupUids));
    }

    @RequestMapping(value = "/full/{userUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<Set<GroupFullDTO>> fetchFullGroupInfo(@PathVariable String userUid,
                                                                @RequestParam(required = false) Set<String> groupUids) {
        final String descriptionTemplate = "Group '%1$s', created on %2$s, has %3$d members, with join code %4$s";
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(userUid, groupUids).stream()
                .map(g -> g.insertDefaultDescriptionIfEmpty(descriptionTemplate)).collect(Collectors.toSet()));
    }


    @RequestMapping(value = "/members", method = RequestMethod.GET)
    public Page<MembershipFullDTO> fetchGroupMembers(@RequestParam String groupUid, Pageable pageable, HttpServletRequest request) {
        User user = getUserFromRequest(request);
        return groupFetchBroker.fetchGroupMembers(user, groupUid, pageable);
    }

    @RequestMapping(value = "/export/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Download an Excel sheet of group members")
    public ResponseEntity<byte[]> exportGroup(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        try {
            String fileName = "group_members.xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            XSSFWorkbook xls = memberDataExportBroker.exportGroup(groupUid, userUid);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xls.write(baos);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            logger.error("IO Exception generating spreadsheet!", e);
            throw new FileCreationException();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
    }

    @ExceptionHandler(value = FileCreationException.class)
    public ResponseEntity<ResponseWrapper> errorGeneratingFile(FileCreationException e) {
        return RestUtil.errorResponse(RestMessage.FILE_GENERATION_ERROR);
    }


    @RequestMapping(value = "/flyer", method = RequestMethod.GET, params = "typeOfFile=PDF", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody //"application/pdf",
    public FileSystemResource genPdf(@RequestParam String groupUid,
                                     @RequestParam boolean color,
                                     @RequestParam Locale language,
                                     @RequestParam String typeOfFile) {
        return generateFlyer(groupUid, color, language, typeOfFile);
    }

    @RequestMapping(value = "/flyer", method = RequestMethod.GET, params = "typeOfFile=JPEG", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public FileSystemResource genImage(@RequestParam String groupUid,
                                       @RequestParam boolean color,
                                       @RequestParam Locale language,
                                       @RequestParam String typeOfFile) {
        return generateFlyer(groupUid, color, language, typeOfFile);
    }

    private FileSystemResource generateFlyer(String groupUid, boolean color, Locale language, String typeOfFile) {
        try {
            return new FileSystemResource(generatingService.generateGroupFlyer(groupUid, color, language, typeOfFile));
        } catch (FileNotFoundException e) {
            log.error("Could not generate flyer!", e);
            return null;
        }
    }
}