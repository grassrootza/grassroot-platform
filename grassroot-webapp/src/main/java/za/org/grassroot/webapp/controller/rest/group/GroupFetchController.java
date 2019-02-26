package za.org.grassroot.webapp.controller.rest.group;

import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupLogDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupWebDTO;
import za.org.grassroot.core.dto.group.MembershipRecordDTO;
import za.org.grassroot.core.dto.membership.MembershipFullDTO;
import za.org.grassroot.core.dto.membership.MembershipStdDTO;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.location.MunicipalFilteringBroker;
import za.org.grassroot.integration.location.Municipality;
import za.org.grassroot.integration.location.UserMunicipalitiesResponse;
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
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.webapp.util.RestUtil.convertWorkbookToDownload;

@RestController
@Grassroot2RestController
@Slf4j @Api("/v2/api/group/fetch")
@RequestMapping(value = "/v2/api/group/fetch")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class GroupFetchController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(GroupFetchController.class);

    private final GroupFetchBroker groupFetchBroker;

    private final MembershipRepository membershipRepository;
    private final MemberDataExportBroker memberDataExportBroker;
    private final MessageSourceAccessor messageSourceAccessor;
    private final GroupBroker groupBroker;

    private MunicipalFilteringBroker municipalFilteringBroker;

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
            .put(Permission.GROUP_PERMISSION_SEND_BROADCAST, 11)
            .put(Permission.GROUP_PERMISSION_CREATE_CAMPAIGN, 12)
            .build();


    @Autowired
    public GroupFetchController(JwtService jwtService, UserManagementService userManagementService,
                                GroupBroker groupBroker, GroupFetchBroker groupFetchBroker,
                                MemberDataExportBroker memberDataExportBroker, MessageSourceAccessor messageSourceAccessor,
                                MembershipRepository membershipRepository) {
        super(jwtService, userManagementService);
        this.groupFetchBroker = groupFetchBroker;
        this.memberDataExportBroker = memberDataExportBroker;
        this.messageSourceAccessor = messageSourceAccessor;
        this.groupBroker = groupBroker;
        this.membershipRepository = membershipRepository;
    }

    @Autowired(required = false)
    public void setMunicipalFilteringBroker(MunicipalFilteringBroker municipalFilteringBroker) {
        this.municipalFilteringBroker = municipalFilteringBroker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ApiOperation("Returns a list of groups for currently logged in user")
    public ResponseEntity<List<GroupWebDTO>> listUserGroups(HttpServletRequest request,
                                                            @RequestParam(required = false) Boolean withSubgroups) {
        String userId = getUserIdFromRequest(request);
        boolean includeSubgroups = withSubgroups != null && withSubgroups;
        log.debug("fetching groups for user with ID, {}, include subgroups : ", userId);
        List<GroupWebDTO> groups = groupFetchBroker.fetchGroupWebInfo(userId, includeSubgroups);
        log.info("found {} groups for user", groups.size());
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @RequestMapping(value = "/details/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<GroupFullDTO> loadFullGroupInfo(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        if (userUid != null) {
            final String descriptionTemplate = "Group '%1$s', created on %2$s, has %3$d members, with join code %4$s";
            GroupFullDTO dto = groupFetchBroker.fetchGroupFullInfo(userUid, groupUid, true, true, false)
                    .insertDefaultDescriptionIfEmpty(descriptionTemplate);
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
     * @param existingGroups The groups that already exist on the client, with the epochMilli time they were last updated
     */
    @RequestMapping(value = "/updated", method = RequestMethod.POST)
    public ResponseEntity<Set<GroupTimeChangedDTO>> fetchUpdatedGroups(HttpServletRequest request, @RequestBody Map<String, Long> existingGroups) {
        logger.info("checking for groups changes, map sent in: {}", existingGroups);
        return ResponseEntity.ok(groupFetchBroker.findNewlyChangedGroups(getUserIdFromRequest(request), existingGroups));
    }

    /**
     * Sends back a small set of information about the groups: names, size, last activity times
     */
    @RequestMapping(value = "/info", method = RequestMethod.POST)
    public ResponseEntity<Set<GroupMinimalDTO>> fetchGroupInfo(HttpServletRequest request,
                                                               @RequestBody(required = false) Set<String> groupUids) {
        return ResponseEntity.ok(groupFetchBroker.fetchGroupNamesUidsOnly(getUserIdFromRequest(request), groupUids));
    }

    @RequestMapping(value = "/minimal", method = RequestMethod.GET)
    public ResponseEntity<List<GroupRefDTO>> fetchMinimalGroupLIst(HttpServletRequest request) {
        return ResponseEntity.ok(groupFetchBroker.fetchGroupNamesUidsOnly(getUserIdFromRequest(request)));
    }

    @RequestMapping(value = "/minimal/filtered", method = RequestMethod.GET)
    @ApiOperation("Returns a list of groups, optionally filtered for a name, sorted by last change time, and optionally paginated, for currently logged in user")
    public ResponseEntity<Page<GroupRefDTO>> listUserGroupsFiltered(HttpServletRequest request,
                                                                    @RequestParam Permission requiredPermission,
                                                                    @RequestParam(required = false) String filterTerm,
                                                                    @RequestParam(required = false) Integer pageNumber) {
        String userId = getUserIdFromRequest(request);
        log.info("Fetching minimal filtered groups for user Id : {}, with page number: {}", userId, pageNumber);
        Pageable pageable = pageNumber == null ? null : PageRequest.of(pageNumber, 10); // might make this a parameter in future
        Page<Group> groups = groupFetchBroker.fetchGroupFiltered(userId, requiredPermission, filterTerm, pageable);
        Page<GroupRefDTO> groupDtos = groups.map(group -> new GroupRefDTO(group, this.membershipRepository));
        return ResponseEntity.ok(groupDtos);
    }

    @RequestMapping(value = "/minimal/specified/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<GroupRefDTO> fetchMinimalDetailsOnGroup(HttpServletRequest request, @PathVariable String groupUid) {
        // todo : may want to permissions check
        Group group = groupFetchBroker.fetchGroupByGroupUid(groupUid);
        return ResponseEntity.ok(new GroupRefDTO(group, this.membershipRepository));
    }

    @RequestMapping(value = "/full", method = RequestMethod.GET)
    @ApiOperation(value = "Get full details about a group, including members (if permission to see details) and description")
    public ResponseEntity<GroupFullDTO> fetchFullGroupInfo(HttpServletRequest request, @RequestParam String groupUid) {
        final String userUid = getUserIdFromRequest(request);
        final GroupFullDTO groupFullDTO = groupFetchBroker.fetchGroupFullInfo(userUid, groupUid, false, false, false);
        return ResponseEntity.ok(groupFullDTO);
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
    public Page<MembershipStdDTO> fetchGroupMembers(@RequestParam String groupUid, Pageable pageable, HttpServletRequest request) {
        User user = getUserFromRequest(request);
        log.info("fetching users, with pageable: {}", pageable);
        return groupFetchBroker.fetchGroupMembers(user, groupUid, pageable).map(MembershipStdDTO::new);
    }

    @RequestMapping(value = "/members/filter", method = RequestMethod.GET)
    public GroupFilterResponse filterGroupMembers(@RequestParam String groupUid,
                                                     @RequestParam int maxEntities,
                                                     @RequestParam (required = false) Collection<Province> provinces,
                                                     @RequestParam (required = false) Boolean noProvince,
                                                     @RequestParam (required = false) Collection<String> taskTeams,
                                                     @RequestParam (required = false) Collection<String> topics,
                                                     @RequestParam (required = false) Collection<String> affiliations,
                                                     @RequestParam (required = false) Collection<GroupJoinMethod> joinMethods,
                                                     @RequestParam (required = false) Collection<String> joinedCampaignsUids,
                                                     @RequestParam (required = false) Integer joinDaysAgo,
                                                     @RequestParam (required = false)  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  LocalDate joinDate,
                                                     @RequestParam (required = false) JoinDateCondition joinDaysAgoCondition,
                                                     @RequestParam (required = false) String namePhoneOrEmail,
                                                     @RequestParam (required = false) Collection<String> languages,
                                                     @RequestParam (required = false) Integer municipalityId,
                                                     HttpServletRequest request) {
        log.info("filtering, name phone or email = {}", namePhoneOrEmail);
        log.info("Do we have municipality ID? {}",municipalityId);

        List<Membership> memberships = groupFetchBroker.filterGroupMembers(getUserFromRequest(request), groupUid,
                provinces, noProvince, taskTeams, topics, affiliations, joinMethods, joinedCampaignsUids,
                joinDaysAgo, joinDate, joinDaysAgoCondition, namePhoneOrEmail, languages);

        if (municipalFilteringBroker != null && municipalityId != null) {
            List<Membership> membershipsInMunicipality = municipalFilteringBroker.getMembersInMunicipality(groupUid,municipalityId + "");
            memberships.retainAll(membershipsInMunicipality);
            log.info("Members in Municipality with id: {} is: {}",municipalityId,membershipsInMunicipality);
        }
        // if this becomes non-performant, use a projection
        GroupFilterResponse response = new GroupFilterResponse();
        List<MembershipStdDTO> dtos = memberships.stream().map(MembershipStdDTO::new).collect(Collectors.toList());
        // dtos are now in memory so this is fine
        response.setNumberSms(dtos.stream().filter(MembershipStdDTO::hasPhone).count());
        response.setNumberEmail(dtos.stream().filter(MembershipStdDTO::hasEmail).count());
        response.setNumberSmsAndEmail(dtos.stream().filter(MembershipStdDTO::hasBoth).count());
        response.setTotalElements(dtos.size());
        // sublist too fragile, hence using this, though looks slightly clumsy
        response.setContent(dtos.stream().limit(maxEntities).collect(Collectors.toList()));

        return response;
    }

    @RequestMapping(value = "/members/filter/download/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<byte[]> downloadFilteredGroupMembers(@PathVariable String groupUid,
                                                               @RequestBody List<String> filteredMemberUids,
                                                               HttpServletRequest request) {
        log.info("filtering & downloading, member UIDs = {}", filteredMemberUids);
        String fileName = "filtered_members.xls";
        XSSFWorkbook xls = memberDataExportBroker.exportGroupMembersFiltered(groupUid, getUserIdFromRequest(request),
                filteredMemberUids);
        return convertWorkbookToDownload(fileName, xls);
    }

    @RequestMapping(value = "/members/new", method = RequestMethod.GET)
    @ApiOperation(value = "Returns members joined recently to groups where logged in user has permission to see member details")
    public ResponseEntity<Page<MemberFrontPageInfo>> getRecentlyJoinedUsers(@RequestParam(required = false) Integer howRecentInDays, HttpServletRequest request, Pageable pageable) {

        int daysLimit = howRecentInDays != null ? howRecentInDays : 7;
        User loggedInUser = getUserFromRequest(request);
        log.info("fetching users with pageable: {}", pageable);
        Page<MemberFrontPageInfo> page = groupFetchBroker
                .fetchUserGroupsNewMembers(loggedInUser, Instant.now().minus(daysLimit, ChronoUnit.DAYS), pageable)
                .map(MemberFrontPageInfo::new);
        log.info("number users back: {}", page.getNumberOfElements());
        return ResponseEntity.ok(page);
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
        String fileName = "group_members.xlsx";
        XSSFWorkbook xls = memberDataExportBroker.exportGroup(groupUid, userUid);
        return convertWorkbookToDownload(fileName, xls);
    }

    @ExceptionHandler(value = FileCreationException.class)
    public ResponseEntity<ResponseWrapper> errorGeneratingFile(FileCreationException e) {
        return RestUtil.errorResponse(RestMessage.FILE_GENERATION_ERROR);
    }

    @RequestMapping(value = "/permissions/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<Map<GroupRole, List<PermissionDTO>>> fetchPermissions(@PathVariable String groupUid,
																				@RequestParam Set<GroupRole> roleNames,
																				HttpServletRequest request) {

        User user = getUserFromRequest(request);
        Map<GroupRole, List<PermissionDTO>> permissions = new HashMap<>();
        if (user != null) {
            Group group = groupBroker.load(groupUid);

            List<PermissionDTO> permissionsDTO = new ArrayList<>();
            for (GroupRole roleName : roleNames) {
                Set<Permission> permissionsEnabled = group.getPermissions(roleName);
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

    @RequestMapping(value = "/inbound-messages/{groupUid}", method = RequestMethod.GET)
    public Page<GroupLogDTO> fetchInboundMessagesByGroupUid(@PathVariable String groupUid,
                                                              @RequestParam (required = false) Long from,
                                                              @RequestParam (required = false) Long to,
                                                              @RequestParam (required = false) String keyword,
                                                              Pageable pageable, HttpServletRequest request) {
            User user = getUserFromRequest(request);
            Group group = groupFetchBroker.fetchGroupByGroupUid(groupUid);
            return  groupFetchBroker.getInboundMessageLogs(user, group, from != null ? Instant.ofEpochMilli(from) : null, to != null ? Instant.ofEpochMilli(to) : null, keyword, pageable);
    }

    @RequestMapping(value = "/inbound-messages/{groupUid}/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> fetchInboundMessagesForDownloadByGroupUid(@PathVariable String groupUid,
                                                            @RequestParam (required = false) Long from,
                                                            @RequestParam (required = false) Long to,
                                                            @RequestParam (required = false) String keyword,
                                                            HttpServletRequest request) {

        try {
            User user = getUserFromRequest(request);
            Group group = groupFetchBroker.fetchGroupByGroupUid(groupUid);

            String fileName = "inbound_messages.xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            List<GroupLogDTO> inboundMessages = groupFetchBroker.getInboundMessagesForExport(user, group, from != null ? Instant.ofEpochMilli(from) : null, to != null ? Instant.ofEpochMilli(to) : null, keyword);

            XSSFWorkbook xls = memberDataExportBroker.exportInboundMessages(inboundMessages);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xls.write(baos);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            logger.error("IO Exception generating spreadsheet!", e);
            throw new FileCreationException();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }


    }

    @RequestMapping(value = "/members/error-report/{groupUid}/download", method = RequestMethod.GET)
    public ResponseEntity<byte[]> fetchGroupMembersWithErrorForDownloadByGroupUid(@PathVariable String groupUid,
            HttpServletRequest request) {

        try {
            User user = getUserFromRequest(request);

            String fileName = "error_report.xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            headers.add("Cache-Control", "no-cache");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            XSSFWorkbook xls = memberDataExportBroker.exportGroupErrorReport(groupUid, user.getUid());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xls.write(baos);
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (IOException e) {
            logger.error("IO Exception generating spreadsheet!", e);
            throw new FileCreationException();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }
// fetch all the municipalities in the province provided
    @RequestMapping(value = "/province/municipalities",method = RequestMethod.GET)
    @ApiOperation(value = "Loads the municipalities for the provided province")
    public  ResponseEntity<List<Municipality>> getMunicipalities(@RequestParam String province){
        logger.info("Province recieved = {}",province);
        if(province.equals("undefined")){
            return ResponseEntity.ok(Collections.emptyList());
        }

        Province province1 = Province.valueOf(province);
        return ResponseEntity.ok(municipalFilteringBroker.getMunicipalitiesForProvince(province1));
    }
//    Getting the users for a certain municipalities
    @RequestMapping(value = "/members/location",method = RequestMethod.GET)
    @ApiOperation(value = "Loads the municipalities for users that have location in a group")
    public ResponseEntity<UserMunicipalitiesResponse> loadUsersWithLocation(@RequestParam String groupUid){
        Group group = groupBroker.load(groupUid);
        Set<String> memberUids = group.getMembers().stream().map(User::getUid).collect(Collectors.toSet());
        UserMunicipalitiesResponse userMunicipalitiesResponse = municipalFilteringBroker.getMunicipalitiesForUsersWithLocationFromCache(memberUids);
        return ResponseEntity.ok(userMunicipalitiesResponse);
    }
    //Fetching count for all the users that have gps coordinates
    @RequestMapping(value = "/users/location/timeStamp", method = RequestMethod.GET)
    @ApiOperation(value = "Fetching the number of users who have coordinates with a specific time stamp period ")
    public ResponseEntity<Long> countByTimestampGreaterThan(@RequestParam boolean countAll){
        return ResponseEntity.ok(municipalFilteringBroker.countUserLocationLogs(countAll, true));
    }

}
