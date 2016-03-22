package za.org.grassroot.webapp.util;

import za.org.grassroot.core.domain.Permission;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/03/22.
 */
public class RestUtil {


    public static Set<Permission> filterPermissions(Set<Permission> permissions, String filterBy){
        return permissions.stream().filter(p -> p.toString().contains(filterBy)).collect(Collectors.toSet());

    }
}
