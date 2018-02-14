package za.org.grassroot.webapp.controller.rest.group;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.*;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.exception.FileCreationException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.MemberActivityDTO;
import za.org.grassroot.webapp.model.rest.PermissionDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final MessageSourceAccessor messageSourceAccessor;
    private final GroupBroker groupBroker;

    private final static ImmutableMap<Permission, Integer> permissionsDisplayed = ImmutableMap.<Permission, Integer>builder()
            .put(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS, 1)
            .put(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING, 2)
            .put(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 3)
            .put(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 4)
            .put(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER, 5)
            .put(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 6)
            .put(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 7)
            .put(Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE, 8)
            .put(Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK, 9)
            .put(Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS, 10)
            .put(Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS, 11)
            .build();


    public GroupFetchController(GroupFetchBroker groupFetchBroker, JwtService jwtService,
                                UserManagementService userManagementService, PdfGeneratingService generatingService,
                                MemberDataExportBroker memberDataExportBroker, MessageSourceAccessor messageSourceAccessor,
                                GroupBroker groupBroker) {
        super(jwtService, userManagementService);
        this.groupFetchBroker = groupFetchBroker;
        this.generatingService = generatingService;
        this.memberDataExportBroker = memberDataExportBroker;
        this.messageSourceAccessor = messageSourceAccessor;
        this.groupBroker = groupBroker;
    }

    @RequestMapping(value = "/list")
    @ApiOperation("Returns a list of groups for currently logged in user")
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

    @RequestMapping(value = "/details/taskteam/{parentUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a task team, including members (if permission to see details on parent or child) and description")
    public ResponseEntity<GroupFullDTO> loadFullGroupInfo(@PathVariable String parentUid,
                                                          @RequestParam String taskTeamUid,
                                                          HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        if (userUid != null) {
            GroupFullDTO dto = groupFetchBroker.fetchSubGroupDetails(userUid, parentUid, taskTeamUid);
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

    @RequestMapping(value = "/full", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<GroupFullDTO> fetchFullGroupInfo(HttpServletRequest request, @RequestParam String groupUid) {
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(getUserIdFromRequest(request),
                groupUid));
    }

    @RequestMapping(value = "/members/history/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get the record of member changes up to some period of time in the past")
    public ResponseEntity<List<MembershipRecordDTO>> fetchMemberChangeRecords(HttpServletRequest request,
                                                                              @PathVariable String groupUid,
                                                                              @RequestParam long startDateTimeEpochMillis) {
        return ResponseEntity.ok(groupFetchBroker.fetchRecentMembershipChanges(getUserIdFromRequest(request),
                groupUid, Instant.ofEpochMilli(startDateTimeEpochMillis)));
    }

    @RequestMapping(value = "/members", method = RequestMethod.GET)
    public Page<MembershipFullDTO> fetchGroupMembers(@RequestParam String groupUid, Pageable pageable, HttpServletRequest request) {
        User user = getUserFromRequest(request);
        return groupFetchBroker.fetchGroupMembers(user, groupUid, pageable);
    }

    @RequestMapping(value = "/members/filter", method = RequestMethod.GET)
    public List<MembershipFullDTO> filterGroupMembers(@RequestParam String groupUid,
                                                      @RequestParam (required = false) Collection<Province> provinces,
                                                      @RequestParam (required = false) Collection<String> taskTeams,
                                                      @RequestParam (required = false) Collection<String> topics,
                                                      @RequestParam (required = false) Collection<String> affiliations,
                                                      @RequestParam (required = false) Collection<GroupJoinMethod> joinMethods,
                                                      @RequestParam (required = false) Collection<String> joinedCampaignsUids,
                                                      @RequestParam (required = false) Integer joinDaysAgo,
                                                      @RequestParam (required = false)  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  LocalDate joinDate,
                                                      @RequestParam (required = false) JoinDateCondition joinDaysAgoCondition,
                                                      @RequestParam (required = false) String namePhoneOrEmail,
                                                      HttpServletRequest request) {
        return groupFetchBroker.filterGroupMembers(getUserFromRequest(request), groupUid,
                provinces, taskTeams, topics, affiliations, joinMethods, joinedCampaignsUids,
                joinDaysAgo, joinDate, joinDaysAgoCondition, namePhoneOrEmail).stream().map(MembershipFullDTO::new).collect(Collectors.toList());
    }

    @RequestMapping(value = "/members/new", method = RequestMethod.GET)
    @ApiOperation(value = "Returns members joined recently to groups where logged in user has permission to see member details")
    public ResponseEntity<Page<MembershipFullDTO>> getRecentlyJoinedUsers(@RequestParam(required = false) Integer howRecentInDays, HttpServletRequest request, Pageable pageable) {

        howRecentInDays = howRecentInDays != null ? howRecentInDays : 7;
        User loggedInUser = getUserFromRequest(request);
        if (loggedInUser != null) {
            log.info("fetching users with pageable: {}", pageable);
            Page<MembershipFullDTO> page = groupFetchBroker
                    .fetchUserGroupsNewMembers(loggedInUser, Instant.now().minus(howRecentInDays, ChronoUnit.DAYS), pageable)
                    .map(MembershipFullDTO::new);
            log.info("number users back: {}", page.getSize());
            return ResponseEntity.ok(page);
        } else
            return new ResponseEntity<>((Page<MembershipFullDTO>) null, HttpStatus.FORBIDDEN);
    }

    @RequestMapping(value = "/members/activity/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Returns a detailed activity list for a member")
    public List<MemberActivityDTO> getMemberActivty(@PathVariable String groupUid, @RequestParam String memberUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        try {
            List<ActionLog> actionLogs = groupFetchBroker.fetchUserActivityDetails(userUid, groupUid, memberUid);
            return actionLogs.stream().map(al -> new MemberActivityDTO(memberUid, groupUid, al))
                    .collect(Collectors.toList());
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
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

    @RequestMapping(value = "/permissions/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<HashMap<String, List<PermissionDTO>>> fetchPermissions(@PathVariable String groupUid,
                                                                @RequestParam Set<String> roleNames,
                                                                HttpServletRequest request) {

        User user = getUserFromRequest(request);
        HashMap<String, List<PermissionDTO>> permissions = new HashMap<>();
        if (user != null) {
            Group group = groupBroker.load(groupUid);

            List<PermissionDTO> permissionsDTO = new ArrayList<>();
            for (String roleName : roleNames) {
                Set<Permission> permissionsEnabled = group.getRole(roleName).getPermissions();
                permissionsDTO = permissionsDisplayed.keySet().stream()
                        .map(permission -> new PermissionDTO(permission, group, roleName, permissionsEnabled, messageSourceAccessor))
                        .sorted()
                        .collect(Collectors.toList());
                permissions.put(roleName, permissionsDTO);
            }

            return  new ResponseEntity<>(permissions, HttpStatus.OK);
        } else
            return new ResponseEntity<>(permissions, HttpStatus.UNAUTHORIZED);

    }

    @RequestMapping(value = "/permissions-displayed", method = RequestMethod.GET)
    public ResponseEntity<Set<Permission>> fetchPermissionsDisplayed(HttpServletRequest request) {

        User user = getUserFromRequest(request);
        Set<Permission> permissions = new HashSet<>();
        if (user != null) {
            permissions = permissionsDisplayed.keySet();
            return  new ResponseEntity<>(permissions, HttpStatus.OK);
        } else
            return new ResponseEntity<>(permissions, HttpStatus.UNAUTHORIZED);

    }

    @RequestMapping(value = "/member/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch group member by memberUid", notes = "Requires GROUP_PERMISSION_SEE_MEMBER_DETAILS")
    public ResponseEntity<MembershipFullDTO> fetchMemberByMemberUid(HttpServletRequest request, @PathVariable String groupUid,
                                                                    @RequestParam String memberUid) {
        try {
            String userId = getUserIdFromRequest(request);
            return ResponseEntity.ok(groupFetchBroker.fetchGroupMember(userId, groupUid, memberUid));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
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