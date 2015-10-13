package za.org.grassroot.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
//import org.springframework.security.acls.model.*;
import org.springframework.security.acls.model.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private MutableAclService mutableAclService;


    @Override
    public void addUserGroupPermissions(Group group, User user, Set<Permission> groupPermissions) {

        try {

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user.getUsername(),
                    user.getPassword(), user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);


            ObjectIdentity objectIdentity = new ObjectIdentityImpl(Group.class, group.getId());
            Sid sid = new PrincipalSid(user.getUsername());

            MutableAcl acl = mutableAclService.createAcl(objectIdentity);

            acl = (MutableAcl) mutableAclService.readAclById(objectIdentity);

            /**************************************************************
             * Grant some permissions via an access control entry (ACE)
             **************************************************************/

            for (Permission permission : groupPermissions) {

                acl.insertAce(acl.getEntries().size(), permission, sid, true);
                mutableAclService.updateAcl(acl);
            }


        } catch (Exception e) {
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
}
