package za.org.grassroot.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.domain.PrincipalSid;
//import org.springframework.security.acls.model.*;
import org.springframework.security.acls.model.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;

import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Service
@Transactional
public class GroupAccessControlManager implements GroupAccessControlManagementService {

    private static Logger log = LoggerFactory.getLogger(GroupAccessControlManager.class);

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    @Qualifier("aclPermissionFactory")
    private PermissionFactory permissionFactory;

    @Autowired
    private MutableAclService mutableAclService;


    @Override
    public void addUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions) {

        try {

            log.info("ZOG: Adding permissions for this group ... " + group.toString());
            log.info("ZOG: Adding them to this user ... " + user.toString());
            log.info("ZOG: Adding this set of permissions ... " + groupPermissions);


            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                authentication = new UsernamePasswordAuthenticationToken(user,
                        user.getPassword(), user.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            ObjectIdentity objectIdentity = new ObjectIdentityImpl(Group.class, group.getId());
            Sid sid = new PrincipalSid(user.getUsername());

            MutableAcl acl;

            try {
                acl = (MutableAcl) mutableAclService.readAclById(objectIdentity);
            } catch (NotFoundException e) {

                acl = mutableAclService.createAcl(objectIdentity);
                acl = (MutableAcl) mutableAclService.readAclById(objectIdentity);

            }

            /**************************************************************
             * Grant some permissions via an access control entry (ACE)
             **************************************************************/

            for (Permission permission : groupPermissions) {

                acl.insertAce(acl.getEntries().size(), permission, sid, true);
                mutableAclService.updateAcl(acl);
            }


        } catch (Exception e) {
            log.error("Could not add group permissions for user", e);
            throw new RuntimeException("Could not add group permissions for user", e);
        }


    }

    @Override
    public void removeUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions) {

        try {
            MutableAcl acl = (MutableAcl) mutableAclService.readAclById(new ObjectIdentityImpl(Group.class, group.getId()));
            Sid sid = new PrincipalSid(user.getUsername());


            for (Permission permission : groupPermissions) {
                deleteAce(acl, permission, sid);
            }

            acl = mutableAclService.updateAcl(acl);

            log.info("Deleted group {} ACL permissions for recipient {}", group.getGroupName(), sid);
        } catch (Exception e) {
            throw new RuntimeException("Could not remove group permissions for user", e);
        }

    }

    private void deleteAce(MutableAcl acl, Permission permission, Sid sid) {
        List<AccessControlEntry> entries = ImmutableList.copyOf(acl.getEntries());
        int                      index   = 0;
        for (AccessControlEntry accessControlEntry : entries) {
            if (accessControlEntry.getSid().equals(sid) && accessControlEntry.getPermission().equals(permission)) {
                acl.deleteAce(index);
            }
            index++;

        }
    }

    @Override
    public boolean hasGroupPermission(Permission permission, Group group, User user) {

        try {
            ObjectIdentity objectIdentity = new ObjectIdentityImpl(Group.class, group.getId());

            ImmutableList<Sid> sids = ImmutableList.of(new PrincipalSid(user.getUsername()));
            MutableAcl acl = (MutableAcl) mutableAclService.readAclById(objectIdentity, sids);

            return !acl.getEntries().isEmpty() && acl.isGranted(ImmutableList.of(permission), sids, false);

        } catch (NotFoundException nfe) {
            log.warn("Returning false - No ACLs apply for this principal");
            return false;
        }

    }

    /**
     * @param groupId
     * @param permission
     * @return Group if the permission is granted
     * @throws AccessDeniedException if the permission is NOT granted
     */
    @Override
    public Group loadGroup(Long groupId, Permission permission) {

        Group group = groupManagementService.loadGroup(groupId);

        if (group == null) {
            throw new IllegalArgumentException("Group '" + groupId + "' does not exist.");
        }

        if (!permissionEvaluator.hasPermission(SecurityContextHolder.getContext().getAuthentication(), group, permission)) {
            throw new AccessDeniedException("Unauthorised access '" + permission.getAuthority() + "' for Group '" + group.getGroupName() + "'");
        }
        return group;
    }

    @Override
    public Group loadGroup(Long groupId, String permissionName) {

        org.springframework.security.acls.model.Permission permission = permissionFactory.buildFromName(permissionName);
        return loadGroup(groupId, (Permission) permission);
    }

    @Override
    public void updateUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions) {

        throw new RuntimeException("Not implemented yet");
    }


}
