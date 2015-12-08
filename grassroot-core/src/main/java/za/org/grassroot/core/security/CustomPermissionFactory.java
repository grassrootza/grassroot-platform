package za.org.grassroot.core.security;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.CumulativePermission;
import org.springframework.security.acls.domain.DefaultPermissionFactory;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.repository.PermissionRepository;

import java.util.*;

/**
 * @author Lesetse Kimwaga
 */


public class CustomPermissionFactory implements PermissionFactory {


    private PermissionRepository permissionRepository;

    private final Map<Integer, org.springframework.security.acls.model.Permission> registeredPermissionsByInteger = new HashMap<Integer, org.springframework.security.acls.model.Permission>();
    private final Map<String, org.springframework.security.acls.model.Permission>  registeredPermissionsByName    = new HashMap<String, org.springframework.security.acls.model.Permission>();


    public CustomPermissionFactory(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
        registerPermissions();

    }


    @Override
    public org.springframework.security.acls.model.Permission buildFromMask(int mask) {
        if (registeredPermissionsByInteger.containsKey(Integer.valueOf(mask))) {
            // The requested mask has an exact match against a statically-defined Permission, so return it
            return registeredPermissionsByInteger.get(Integer.valueOf(mask));
        }

        // To get this far, we have to use a CumulativePermission
        CumulativePermission permission = new CumulativePermission();

        for (int i = 0; i < 32; i++) {
            int permissionToCheck = 1 << i;

            if ((mask & permissionToCheck) == permissionToCheck) {
                org.springframework.security.acls.model.Permission p = registeredPermissionsByInteger.get(Integer.valueOf(permissionToCheck));

                if (p == null) {
                    throw new IllegalStateException("Mask '" + permissionToCheck + "' does not have a corresponding static Permission");
                }
                permission.set(p);
            }
        }
        return permission;
    }

    @Override
    public org.springframework.security.acls.model.Permission buildFromName(String name) {

        org.springframework.security.acls.model.Permission p = registeredPermissionsByName.get(name);

        if (p == null) {
            throw new IllegalArgumentException("Unknown permission '" + name + "'");
        }
        return p;
    }

    @Override
    public List<org.springframework.security.acls.model.Permission> buildFromNames(List<String> names) {

        if ((names == null) || (names.size() == 0)) {
            return Collections.emptyList();
        }

        List<org.springframework.security.acls.model.Permission> permissions = new ArrayList<org.springframework.security.acls.model.Permission>(names.size());

        for (String name : names) {
            permissions.add(buildFromName(name));
        }

        return permissions;
    }

    private void registerPermissions() {
        List<Permission> permissions = Lists.newArrayList(permissionRepository.findAll());

        for (Permission permission : permissions) {
            registerPermission(permission, permission.getName());
        }
    }

    public void registerPermissions(Collection<Permission> permissions)
    {
        for (Permission permission : permissions) {
            registerPermission(permission, permission.getName());
        }
    }

    protected void registerPermission(org.springframework.security.acls.model.Permission perm, String permissionName) {
        Assert.notNull(perm, "Permission required");
        Assert.hasText(permissionName, "Permission name required");

        Integer mask = Integer.valueOf(perm.getMask());

        // Ensure no existing Permission uses this integer or code
        Assert.isTrue(!registeredPermissionsByInteger.containsKey(mask), "An existing Permission already provides mask " + mask);
        Assert.isTrue(!registeredPermissionsByName.containsKey(permissionName), "An existing Permission already provides name '" + permissionName + "'");

        // Register the new Permission
        registeredPermissionsByInteger.put(mask, perm);
        registeredPermissionsByName.put(permissionName, perm);
    }
}
