package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupAdminDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;


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

    public AdminRestController(UserManagementService userManagementService,
                               JwtService jwtService,
                               AdminService adminService,
                               GroupRepository groupRepository,
                               GroupBroker groupBroker,
                               MessagingServiceBroker messagingServiceBroker,
                               PasswordTokenService passwordTokenService){
        super(jwtService,userManagementService);
        this.adminService = adminService;
        this.userManagementService = userManagementService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.passwordTokenService = passwordTokenService;
        this.groupRepository = groupRepository;
        this.groupBroker = groupBroker;
        this.jwtService = jwtService;
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
            groups.forEach(group -> groupAdminDTOS.add(new GroupAdminDTO(group)));
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
    public ResponseEntity addMemberToGroup(@RequestParam String groupUid, @RequestParam String displayName,
                                                        @RequestParam String phoneNumber, @RequestParam String roleName,
                                                        @RequestParam String email, @RequestParam String province,
                                                        HttpServletRequest request){
        User user;
        try{
            user = userManagementService.findByNumberOrEmail(phoneNumber,email);
        } catch (NoSuchUserException e){
            log.info("User not found");
            user = null;
        }
        Group group = groupRepository.findOneByUid(groupUid);
        RestMessage restMessage;
        MembershipInfo membershipInfo;

        if(user != null && group.hasMember(user)){
            log.info("User was found and is part of group,updating only");
            Membership membership = group.getMembership(user);
            if(!user.hasPassword() || !user.isHasSetOwnName()){
                groupBroker.updateMembershipDetails(getUserIdFromRequest(request),groupUid,membership.getUser().getUid(),displayName,phoneNumber,email,Province.valueOf(province));
                restMessage = RestMessage.UPDATED;
            } else {
                groupBroker.updateMembershipRole(getUserIdFromRequest(request), groupUid, user.getUid(), roleName);
                restMessage = RestMessage.UPDATED;
            }
        }else{
            log.info("User not found in database,creating membership and adding to group");
            membershipInfo = new MembershipInfo(phoneNumber, roleName, displayName);
            membershipInfo.setProvince(Province.valueOf(province));
            membershipInfo.setMemberEmail(email);
            adminService.addMemberToGroup(getUserIdFromRequest(request), groupUid, membershipInfo);
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
        claims.put(JwtService.SYSTEM_ROLE_KEY, BaseRoles.ROLE_SYSTEM_CALL);
        tokenRequest.setClaims(claims);
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }

}
