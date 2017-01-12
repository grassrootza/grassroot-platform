package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.AccountWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

/**
 * Created by luke on 2017/01/11.
 */
@Controller
@RequestMapping(value = "/api/extra", produces = MediaType.APPLICATION_JSON_VALUE)
public class GRExtraRestController {

    private final UserManagementService userService;
    private final AccountBroker accountBroker;

    @Autowired
    public GRExtraRestController(UserManagementService userService, AccountBroker accountBroker) {
        this.userService = userService;
        this.accountBroker = accountBroker;
    }

    @GetMapping("settings/fetch/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> getAccountSettings(@PathVariable String phoneNumber) {
        User user = userService.findByInputNumber(phoneNumber);
        Account account = accountBroker.loadUsersAccount(user.getUid());

        if (account == null) {
           return RestUtil.messageOkayResponse(RestMessage.MESSAGE_SETTING_NOT_FOUND);
        } else {
            return RestUtil.okayResponseWithData(account.isEnabled() ? RestMessage.ACCOUNT_ENABLED : RestMessage.ACCOUNT_DISABLED,
                    new AccountWrapper(account, user));
        }
    }

}
