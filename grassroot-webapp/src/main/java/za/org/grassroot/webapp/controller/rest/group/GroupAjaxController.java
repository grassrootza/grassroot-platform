package za.org.grassroot.webapp.controller.rest.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/10/31.
 */
@Controller
@RequestMapping(value = "/ajax/group")
public class GroupAjaxController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupAjaxController.class);

    private static final String TODOS_LEFT_FIELD = "todos_left";
    private static final String IS_PAID_FOR = "is_paid_for";
    private static final String USER_HAS_ACCOUNT = "user_has_account";
    private static final String CAN_ADD_GROUPS_TO_ACCOUNT = "can_add_to_account";

    private AccountBroker accountBroker;
    private AccountGroupBroker accountGroupBroker;

    @Autowired
    public GroupAjaxController(AccountBroker accountBroker, AccountGroupBroker accountGroupBroker) {
        this.accountBroker = accountBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @RequestMapping("/limit/todos")
    public @ResponseBody Map<String, Object> todosLeftForGroup(@RequestParam String groupUid) {
        Map<String, Object> returnMap = new HashMap<>();
        returnMap.put(TODOS_LEFT_FIELD, accountGroupBroker.numberTodosLeftForGroup(groupUid));
        return addStandardFields(returnMap, groupUid);
    }

    @RequestMapping("/account/add")
    public @ResponseBody Boolean addGroupToAccount(@RequestParam String groupUid) {
        Account userAccount = accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), false);
        if (userAccount.isEnabled()) {
            logger.info("adding group to account!");
            accountGroupBroker.addGroupToAccount(userAccount.getUid(), groupUid, getUserProfile().getUid());
            return true;
        } else {
            return false;
        }
    }

    private Map<String, Object> addStandardFields(Map<String, Object> map, String groupUid) {
        Account groupAccount = accountGroupBroker.findAccountForGroup(groupUid);
        Account userAccount = accountBroker.loadPrimaryAccountForUser(getUserProfile().getUid(), false);

        map.put(IS_PAID_FOR, groupAccount != null);
        map.put(USER_HAS_ACCOUNT, userAccount != null);
        map.put(CAN_ADD_GROUPS_TO_ACCOUNT, userAccount != null && (accountGroupBroker.numberGroupsLeft(userAccount.getUid()) > 0));
        return map;
    }

}
