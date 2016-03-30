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

    public static int getReminderMinutes(int option) {
        int reminderMins;
        switch (option) {
            case '0':
                reminderMins = 5;
                break;
            case '1':
                reminderMins = 60;
                break;
            case '2':
                reminderMins = 60 * 12;
                break;
            case '3':
                reminderMins = 60 * 24;
                break;
            default:
                reminderMins = 5;
                break;
        }
        return reminderMins;
    }
}

