package za.org.grassroot.core.security;

import org.springframework.security.acls.domain.PermissionFactory;
import za.org.grassroot.core.domain.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class CustomPermissionFactory implements PermissionFactory {

    public CustomPermissionFactory() {
    }

    @Override
    public org.springframework.security.acls.model.Permission buildFromMask(int mask) {
        return Permission.valueOfMask(mask);
    }

    @Override
    public org.springframework.security.acls.model.Permission buildFromName(String name) {
        return Permission.valueOf(name);
    }

    @Override
    public List<org.springframework.security.acls.model.Permission> buildFromNames(List<String> names) {
        if (names == null || names.size() == 0) {
            return Collections.emptyList();
        }

        List<org.springframework.security.acls.model.Permission> permissions = new ArrayList<>(names.size());
        for (String name : names) {
            permissions.add(buildFromName(name));
        }

        return permissions;
    }
}
