package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/10/31.
 */
@Controller
@RequestMapping(value = "/ajax/group/limit")
public class GroupLimitAjaxController extends BaseController {

    private final static String TODOS_LEFT_FIELD = "todos_left";
    private final static String IS_PAID_FOR = "is_paid_for";
    private final static String CAN_ADD_GROUPS_TO_ACCOUNT = "can_add_to_account";

    private AccountGroupBroker accountGroupBroker;

    @Autowired
    public GroupLimitAjaxController(AccountGroupBroker accountGroupBroker) {
        this.accountGroupBroker = accountGroupBroker;
    }

    @RequestMapping("/todos")
    public @ResponseBody Map<String, Object> todosLeftForGroup(@RequestParam String groupUid) {
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.put(TODOS_LEFT_FIELD, accountGroupBroker.numberTodosLeftForGroup(groupUid));
        return addStandardFields(returnMap, groupUid);
    }

    private Map<String, Object> addStandardFields(Map<String, Object> map, String groupUid) {
        Account account = accountGroupBroker.findAccountForGroup(groupUid);
        map.put(IS_PAID_FOR, account != null);
        map.put(CAN_ADD_GROUPS_TO_ACCOUNT, account != null &&
                (accountGroupBroker.numberGroupsLeft(account.getUid()) > 0));
        return map;
    }

}
