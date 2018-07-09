package za.org.grassroot.webapp.controller.rest.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/10/31.
 */
@Controller @Slf4j
@RequestMapping(value = "/ajax/group")
public class GroupAjaxController extends BaseController {

    private static final String TODOS_LEFT_FIELD = "todos_left";
    private static final String IS_PAID_FOR = "is_paid_for";

    private final AccountGroupBroker accountGroupBroker;

    public GroupAjaxController(UserManagementService userManagementService, PermissionBroker permissionBroker, AccountGroupBroker accountGroupBroker) {
        super(userManagementService, permissionBroker);
        this.accountGroupBroker = accountGroupBroker;
    }

    @RequestMapping("/limit/todos")
    public @ResponseBody Map<String, Object> todosLeftForGroup(@RequestParam String groupUid) {
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.put(TODOS_LEFT_FIELD, accountGroupBroker.numberTodosLeftForGroup(groupUid));
        return addStandardFields(returnMap, groupUid);
    }

    private Map<String, Object> addStandardFields(Map<String, Object> map, String groupUid) {
        Account groupAccount = accountGroupBroker.findAccountForGroup(groupUid);

        map.put(IS_PAID_FOR, groupAccount != null);
        return map;
    }

}
