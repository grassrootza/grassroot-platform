package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.AccountManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;

import java.util.logging.Logger;

/**
 * Created by aakilomar on 1/11/16.
 */
@RestController
@RequestMapping(value = "/api/account")
public class AccountRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountManagementService accountManagementService;

    @RequestMapping(value = "/add/{userid}/{groupid}/{accountname}", method = RequestMethod.POST)
    public String add(@PathVariable("userid") Long userid,
                          @PathVariable("groupid") Long groupid,
                          @PathVariable("accountname") String accountname) {
        Account account = accountManagementService.createAccount(accountname);
        accountManagementService.addGroupToAccount(account,groupManagementService.loadGroup(groupid),userRepository.findOne(userid));
        return "Created " + account.toString();
    }

}
