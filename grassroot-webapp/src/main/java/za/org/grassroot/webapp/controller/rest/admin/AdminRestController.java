package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupAdminDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.location.MunicipalFilteringBroker;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static za.org.grassroot.webapp.util.RestUtil.convertWorkbookToDownload;


@RestController @Grassroot2RestController
@Slf4j @Api("/v2/api/admin")
@RequestMapping(value = "/v2/api/admin")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AdminRestController extends BaseRestController{

    private final AdminService adminService;
    private final UserManagementService userManagementService;
    private final MessagingServiceBroker messagingServiceBroker;
    private final PasswordTokenService passwordTokenService;
    private final GroupRepository groupRepository;
    private final GroupBroker groupBroker;
    private final JwtService jwtService;

    private final AccountFeaturesBroker accountFeaturesBroker;

    private MemberDataExportBroker memberDataExportBroker;
    private MunicipalFilteringBroker municipalFilteringBroker;
    private final MembershipRepository membershipRepository;

    public AdminRestController(UserManagementService userManagementService,
                               JwtService jwtService,
                               AdminService adminService,
                               GroupRepository groupRepository,
                               GroupBroker groupBroker,
                               MessagingServiceBroker messagingServiceBroker,
                               PasswordTokenService passwordTokenService,
                               AccountFeaturesBroker accountFeaturesBroker,
                               MembershipRepository membershipRepository) {
        super(jwtService,userManagementService);
        this.adminService = adminService;
        this.userManagementService = userManagementService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.passwordTokenService = passwordTokenService;
        this.groupRepository = groupRepository;
        this.groupBroker = groupBroker;
        this.jwtService = jwtService;
        this.accountFeaturesBroker = accountFeaturesBroker;
        this.membershipRepository = membershipRepository;
    }

    @Autowired(required = false) // as it depends on WhatsApp being active
    public void setMemberDataExportBroker(MemberDataExportBroker memberDataExportBroker) {
        this.memberDataExportBroker = memberDataExportBroker;
    }

    @Autowired(required = false)
    public void setMunicipalFilteringBroker(MunicipalFilteringBroker municipalFilteringBroker) {
        this.municipalFilteringBroker = municipalFilteringBroker;
    }

    @RequestMapping(value = "/user/load",method = RequestMethod.GET)
    public ResponseEntity<String> loadUser(@RequestParam() String lookupTerm,
                                                HttpServletRequest request){
        User user = userManagementService.findByUsernameLoose(lookupTerm);
        String userUid = "";
        if(user != null){
            userUid = user.getUid();
            passwordTokenService.triggerOtp(getUserFromRequest(request));
        }
        return ResponseEntity.ok(userUid);
    }

    @RequestMapping(value = "/user/groups/number",method = RequestMethod.GET)
    public ResponseEntity<Integer> getNumberOfGroupsUserIsPartOf(@RequestParam String userUid){
        User user = userManagementService.load(userUid);
        int numberOfGroups = groupRepository.countByMembershipsUserAndActiveTrue(user);
        return ResponseEntity.ok(numberOfGroups);
    }

    @RequestMapping(value = "/user/optout",method = RequestMethod.POST)
    public ResponseEntity<String> userOptout(@RequestParam String userToOptOutUid,
                                             @RequestParam String otpEntered,
                                             HttpServletRequest request){
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }
        adminService.removeUserFromAllGroups(getUserIdFromRequest(request), userToOptOutUid);
        return ResponseEntity.ok("SUCCESS");
    }

    @RequestMapping(value = "/user/pwd/reset",method = RequestMethod.POST)
    public ResponseEntity<String> updateUserPassword(@RequestParam String userToResetUid,
                                                     @RequestParam String otpEntered,
                                                     HttpServletRequest request){
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }

        String newPwd = passwordTokenService.generateRandomPwd();
        adminService.updateUserPassword(getUserIdFromRequest(request), userToResetUid, newPwd);
        //Sending the password to user
        User user = userManagementService.load(userToResetUid);
        if(user.hasPhoneNumber()){
            messagingServiceBroker.sendPrioritySMS("New Grassroot password:"+newPwd,user.getPhoneNumber());
        }
        return ResponseEntity.ok("SUCCESS");
    }

    @RequestMapping(value = "/groups/search",method = RequestMethod.GET)
    public ResponseEntity<List<GroupAdminDTO>> findGroups(@RequestParam String searchTerm){
        List<GroupAdminDTO> groupAdminDTOS = new ArrayList<>();
        if(!StringUtils.isEmpty(searchTerm)){
            List<Group> groups = groupRepository.findByGroupNameContainingIgnoreCase(searchTerm);
            groups.forEach(group -> groupAdminDTOS.add(new GroupAdminDTO(group, membershipRepository)));
            groupAdminDTOS.sort(Comparator.comparing(
                    (Function<GroupAdminDTO, Integer>) GroupRefDTO::getMemberCount).reversed());
        }
        return ResponseEntity.ok(groupAdminDTOS);
    }

    @RequestMapping(value = "/groups/deactivate",method = RequestMethod.POST)
    public ResponseEntity<String> deactivateGroup(@RequestParam String groupUid,
                                                  HttpServletRequest request){
        adminService.updateGroupActive(getUserIdFromRequest(request), groupUid, false);
        return ResponseEntity.ok("SUCCESS");
    }

    @RequestMapping(value = "/groups/activate",method = RequestMethod.POST)
    public ResponseEntity<String> activateGroup(@RequestParam String groupUid,
                                                HttpServletRequest request){
        adminService.updateGroupActive(getUserIdFromRequest(request), groupUid, true);
        return ResponseEntity.ok("SUCCESS");
    }

    @RequestMapping(value = "/groups/member/add",method = RequestMethod.POST)
    public ResponseEntity addMemberToGroup(@RequestParam String groupUid,
                                           @RequestParam String displayName,
                                           @RequestParam GroupRole roleName,
                                           @RequestParam(required = false) String phoneNumber,
                                           @RequestParam(required = false) String email,
                                           @RequestParam(required = false) Province province,
                                           HttpServletRequest request) {
        String msisdn = StringUtils.isEmpty(phoneNumber) ? null : PhoneNumberUtil.convertPhoneNumber(phoneNumber);

        User user;
        try {
            user = userManagementService.findByNumberOrEmail(msisdn,email);
        } catch (NoSuchUserException e) {
            log.info("User not found");
            user = null;
        }

        Group group = groupRepository.findOneByUid(groupUid);
        RestMessage restMessage;
        MembershipInfo membershipInfo;

        final String userUid = getUserIdFromRequest(request);
        if(user != null && user.isMemberOf(group)) {
            log.info("User was found and is part of group,updating only");
            Membership membership = user.getMembership(group);
            if(!user.isHasSetOwnName()){
                groupBroker.updateMembershipDetails(userUid, groupUid, membership.getUser().getUid(), displayName, msisdn, email, province);
                restMessage = RestMessage.UPDATED;
            } else {
                groupBroker.updateMembershipRole(userUid, groupUid, user.getUid(), roleName);
                restMessage = RestMessage.UPDATED;
            }
        } else {
            log.info("User not found in database,creating membership and adding to group");
            membershipInfo = new MembershipInfo(msisdn, roleName, displayName);
            if (province != null)
                membershipInfo.setProvince(province);
            membershipInfo.setMemberEmail(email);
            adminService.addMemberToGroup(userUid, groupUid, membershipInfo);
            restMessage = RestMessage.UPLOADED;
        }
        return ResponseEntity.ok(restMessage.name());
    }

    @RequestMapping(value = "/groups/tokens/recycle", method = RequestMethod.POST)
    public ResponseEntity triggerGroupTokenRecycle() {
        return ResponseEntity.ok(this.adminService.freeUpInactiveJoinTokens());
    }

    @RequestMapping(value = "/token/system/generate", method = RequestMethod.POST)
    public ResponseEntity<String> createApiToken() {
        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.API_CLIENT);
        Map<String, Object> claims = tokenRequest.getClaims();
        claims.put(JwtService.SYSTEM_ROLE_KEY, StandardRole.ROLE_SYSTEM_CALL);
        tokenRequest.setClaims(claims);
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }

    //Generating Excel file for whatsapp subscribed users
    @RequestMapping(value = "/whatsapp/export", method = RequestMethod.GET)
    @ApiOperation(value = "Download an Excel sheet of whatsapp opted in users")
    public ResponseEntity<byte[]> exportWhatsappOptedInUsers() {
        XSSFWorkbook xls = memberDataExportBroker.exportWhatsappOptedInUsers();
        String fileName = "whatsappUsers.xlsx";
        return convertWorkbookToDownload(fileName, xls);
    }


    @RequestMapping(value = "/config/fetch", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> fetchConfigVars() {
        return ResponseEntity.ok(adminService.getCurrentConfigVariables());
    }

    @RequestMapping(value = "/config/fetch/list",method = RequestMethod.GET)
    public ResponseEntity<List<ConfigVariable>> getAllConfigVariables(){
        return ResponseEntity.ok(adminService.getAllConfigVariables());
    }

    @RequestMapping(value = "/config/update", method = RequestMethod.POST)
    public ResponseEntity updateConfigVar(@RequestParam String key,
                                          @RequestParam String value,
                                          @RequestParam String description) {
        adminService.updateConfigVariable(key, value,description);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/config/create", method = RequestMethod.POST)
    public ResponseEntity createConfigVar(@RequestParam String key,
                                          @RequestParam String value,
                                          @RequestParam String description) {
        adminService.createConfigVariable(key, value,description);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/config/delete",method = RequestMethod.POST)
    public ResponseEntity deleteConfigVar(@RequestParam String key) {
        adminService.deleteConfigVariable(key);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/config/fetch/below/limit",method = RequestMethod.GET)
    public ResponseEntity<Integer> countGroupsBelowLimit(@RequestParam(required = false) Integer limit){
        int limitToUse = limit == null ? accountFeaturesBroker.getFreeGroupLimit() : limit;
        return ResponseEntity.ok(accountFeaturesBroker.numberGroupsBelowFreeLimit(limitToUse));
    }

    @RequestMapping(value = "/config/fetch/above/limit",method = RequestMethod.GET)
    public ResponseEntity<Integer> countGroupsAboveLimit(@RequestParam(required = false) Integer limit){
        int limitToUse = limit == null ? accountFeaturesBroker.getFreeGroupLimit() : limit;
        return ResponseEntity.ok(accountFeaturesBroker.numberGroupsAboveFreeLimit(limitToUse));
    }

    @RequestMapping(value = "/update/location/address",method = RequestMethod.GET)
    @ApiOperation(value = "Refreshes the user location log table by adding locations that are in address but not in location log")
    public ResponseEntity updateLocationLogFromAddress(@RequestParam Integer pageSize){
        log.info("Saving location logs from address");
        municipalFilteringBroker.saveLocationLogsFromAddress(pageSize);
        return ResponseEntity.ok(RestMessage.UPDATED);
    }

    // Refreshing the user location log cache for updating user count with gps coordinates
    @RequestMapping(value = "/municipalities/trigger",method = RequestMethod.GET)
    @ApiOperation(value = "Refreshes the municipalities for users with locations cache")
    public ResponseEntity triggerMunicipalityFetch(@RequestParam Integer pageSize) {
        log.info("Updating user municipalities cache, page size: {}", pageSize);
        municipalFilteringBroker.fetchMunicipalitiesForUsersWithLocations(pageSize);
        return ResponseEntity.ok(RestMessage.UPDATED);
    }

}
