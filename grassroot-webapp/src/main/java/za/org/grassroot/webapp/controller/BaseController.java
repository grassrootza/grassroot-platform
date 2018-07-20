package za.org.grassroot.webapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller @Slf4j
public class BaseController {

    protected final UserManagementService userManagementService;
    protected final PermissionBroker permissionBroker;

    @Autowired
    public BaseController(UserManagementService userManagementService, PermissionBroker permissionBroker) {
        this.userManagementService = userManagementService;
        this.permissionBroker = permissionBroker;
    }

    public static Map<String, String> getImplementedLanguages() {
        final LinkedHashMap<String, String> languages = new LinkedHashMap<>();

        languages.put("en", "English");
        languages.put("nso", "Sepedi");
        languages.put("st", "Sesotho");
        languages.put("ts", "Tsonga");
        languages.put("zu", "Zulu");
        languages.put("af", "Afrikaans");

        return languages;
    }
    
}
