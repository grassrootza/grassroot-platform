package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.AddressBroker;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.Set;

import static java.awt.SystemColor.menu;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by paballo on 2016/07/13.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDSafetyGroupController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDSafetyGroupController.class);

    @Autowired
    private AddressBroker addressBroker;

    @Autowired
    private SafetyEventBroker safetyEventBroker;


    private static final String

            createGroupMenu = "create",
            createGroupAddRespondents = "add-numbers",
            safetyGroup = "safety",
            optionsKey = "options",
            join = "join-request",
            addAddress = "add-address",
            viewAddress = "view-address",
            removeAddress = "remove-address",
            changeAddress = "change-address",
            resetSafetyGroup = "reset",
            recordResponse = "record-response",
            recordValidity = "record-validity",
            nominateGroup = "nominate-group";


    private static final String safetyGroupPath = homePath + safetyGroup + "/";
    private static final USSDSection thisSection = USSDSection.SAFETY_GROUP_MANAGER;
    private static final String groupUidParam = "groupUid";


    @RequestMapping(value = safetyGroupPath + startMenu)
    @ResponseBody
    public Request manageSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if (user.hasSafetyGroup()) {
            Group group = user.getSafetyGroup();
            menu = new USSDMenu(getMessage(thisSection, promptKey, "exists", group.getGroupName(), user));
            boolean hasAddress = userManager.hasAddress(user.getUid());
            if (!hasAddress) {
                menu.addMenuOption(thisSection.toPath() + addAddress,
                        getMessage(thisSection, createGroupMenu, optionsKey + ".address", user));
            } else {
                menu.addMenuOption(thisSection.toPath() + viewAddress,
                        getMessage(thisSection, promptKey, viewAddress, user));
            }
            menu.addMenuOption(thisSection.toPath() + createGroupAddRespondents + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "addrespondents", user));
            menu.addMenuOption(thisSection.toPath() + resetSafetyGroup + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "leave", user));
            menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, optionsKey, "back", user));
        } else {

            menu = new USSDMenu(getMessage(thisSection, promptKey, "notexist", user));
            menu.addMenuOption(thisSection.toPath() + nominateGroup, getMessage(thisSection, optionsKey, "createyes", user));
            menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, optionsKey, "createno", user));
        }

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + nominateGroup)
    @ResponseBody
    public Request nominateSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = ussdGroupUtil.askForGroupAllowCreateNew(user, thisSection, nominateGroup + doSuffix, "newgroup",
                createGroupMenu, null);

        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + nominateGroup + doSuffix)
    @ResponseBody
    public Request nominateSafetyGroupDo(@RequestParam String msisdn, @RequestParam(value = groupUidParam, required = true) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        Group group = groupBroker.makeSafetyGroup(user.getUid(), groupUid);
        String prompt = getMessage(thisSection, createGroupMenu, promptKey + ".confirm", group.getGroupName(), user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(thisSection.toPath() + startMenu, "Back");
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, "Group menu");

        return menuBuilder(menu);
    }


    @RequestMapping(value = safetyGroupPath + "newgroup")
    @ResponseBody
    public Request newGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(user, thisSection, createGroupMenu));
    }

    @RequestMapping(value = safetyGroupPath + createGroupMenu)
    @ResponseBody
    public Request createGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = false) String groupUid,
                               @RequestParam(value = userInputParam, required = false) String groupName,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {


        User user = userManager.findByInputNumber(inputNumber);

        USSDMenu menu;
        if (!USSDGroupUtil.isValidGroupName(groupName)) {
            menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, USSDSection.SAFETY_GROUP_MANAGER, createGroupMenu);
        } else {
            Set<MembershipInfo> members = Sets.newHashSet(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
            Group group = groupBroker.create(user.getUid(), groupName, null, members, GroupPermissionTemplate.DEFAULT_GROUP, null, null, true);
            groupBroker.makeSafetyGroup(user.getUid(), group.getUid());
            menu = new USSDMenu("Safety group created, what would you like to do next?");
            menu.addMenuOption(thisSection.toPath() + createGroupAddRespondents + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "addrespondents", user));
            menu.addMenuOption(thisSection.toPath() + resetSafetyGroup + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "leave", user));
            ;
            menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, optionsKey, "back", user));
        }

        return menuBuilder(menu);

    }


    @RequestMapping(value = safetyGroupPath + addAddress)
    @ResponseBody
    public Request addAddress(@RequestParam String msisdn,
                              @RequestParam(value = userInputParam, required = false) String fieldValue,
                              @RequestParam(value = "interrupted", required = false) boolean interrupted,
                              @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        return menuBuilder(getAddressMenu(field, user, fieldValue, false));
    }

    @RequestMapping(value = safetyGroupPath + viewAddress)
    @ResponseBody
    public Request viewAddress(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        Address address = addressBroker.getUserAddress(user.getUid());
        String[] fields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "address.view", promptKey, fields, user));
        menu.addMenuOption(thisSection.toPath() + changeAddress + doSuffix, getMessage(thisSection, "address", optionsKey + ".change", user));
        menu.addMenuOption(thisSection.toPath() + removeAddress, getMessage(thisSection, "address", optionsKey + ".remove", user));

        return menuBuilder(menu);
    }



    @RequestMapping(value = safetyGroupPath + changeAddress)
    @ResponseBody
    public Request changeAddress(@RequestParam String msisdn,
                                 @RequestParam(value = userInputParam, required = true) String fieldValue,
                                 @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                 @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if ("house".equals(field)) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "house");
        } else if ("street".equals(field)) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "street");
        } else {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "town");

        }
        return menuBuilder(menu);

    }


    @RequestMapping(value = safetyGroupPath + changeAddress + doSuffix)
    @ResponseBody
    public Request changeAddressDo(@RequestParam String msisdn,
                                   @RequestParam(value = userInputParam, required = true) String fieldValue,
                                   @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                   @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;

        if(field !=null) {
            switch (field) {
                case "house":
                    addressBroker.updateUserAddress(user.getUid(), fieldValue, null, null);
                    break;
                case "street":
                    addressBroker.updateUserAddress(user.getUid(), null, fieldValue, null);
                    break;
                case "town":
                    addressBroker.updateUserAddress(user.getUid(), null, null, fieldValue);
                    break;
                default:
                    throw new IllegalArgumentException("Address field to be changed cannot be null");
            }
        }
        Address address = addressBroker.getUserAddress(user.getUid());
        String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
        String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
        menu = new USSDMenu(confirmPrompt);
        menu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection, "address.confirm", optionsKey + ".yes", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                getMessage(thisSection, "address.confirm", optionsKey + ".changehouse", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                getMessage(thisSection, "address.confirm", optionsKey + ".changestreet", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                getMessage(thisSection, "address.confirm", optionsKey + ".changearea", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + removeAddress)
    @ResponseBody

    //todo consider moving all address handlers to the user controller
    public Request removeAddress(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "address.remove", promptKey, user));
        menu.addMenuOption(thisSection.toPath() + removeAddress + doSuffix, getMessage(thisSection, "address.remove", optionsKey + ".yes", user));
        menu.addMenuOption(thisSection.toPath() + viewAddress, getMessage(thisSection, "address.remove", optionsKey + ".no", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + removeAddress + doSuffix)
    @ResponseBody
    public Request removeAddressDo(@RequestParam String msisdn) throws Exception {
        User user = userManager.findByInputNumber(msisdn);
        addressBroker.removeAddress(user.getUid());
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "address.remove.confirm", promptKey, user));
        menu.addMenuOption(thisSection.toPath() + addAddress, "Add address");
        menu.addMenuOption(thisSection.toPath() + startMenu, "Go to safety group menu");

        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + createGroupAddRespondents)
    @ResponseBody
    public Request createGroupAddNumbersOpeningPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddRespondents, groupUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, "addrespondents", promptKey, user),
                groupMenuWithId(thisSection, createGroupAddRespondents + doSuffix, groupUid)));
    }

    @RequestMapping(value = safetyGroupPath + createGroupAddRespondents + doSuffix)
    @ResponseBody
    public Request addRespondentsToGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                         @RequestParam(value = groupUidParam, required = true) String groupUid,
                                         @RequestParam(value = userInputParam, required = true) String userInput,
                                         @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

        USSDMenu menu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithInput(createGroupAddRespondents + doSuffix, groupUid, userResponse, false));

        if (!userResponse.trim().equals("0")) {
            menu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
                    userResponse, createGroupAddRespondents + doSuffix);
        } else {
            menu = new USSDMenu(getMessage(thisSection, promptKey, "respondents.confirm", user));
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection, "addrespondents", optionsKey + ".group", user));
            menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu,
                    getMessage(thisSection, "addrespondents", optionsKey + ".home", user));
        }

        return menuBuilder(menu);
    }


    @RequestMapping(value = safetyGroupPath + resetSafetyGroup)
    @ResponseBody
    public Request reset(@RequestParam(value = phoneNumber) String inputNumber,
                         @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "group", promptKey + ".reset", user));
        menu.addMenuOption(thisSection.toPath() + "reset-do?groupUid=" + groupUid, "Yes");
        menu.addMenuOption(thisSection.toPath() + startMenu, "Back");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + resetSafetyGroup + doSuffix)
    @ResponseBody
    public Request resetDo(@RequestParam(value = phoneNumber) String inputNumber,
                           @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        Group group = groupBroker.load(groupUid);
        if (group.getUid().equals(user.getSafetyGroup().getUid())) {
            userManager.setSafetyGroup(user.getUid(), null);
        }
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "group", "reset.confirm", user));
        menu.addMenuOption(thisSection.toPath() + "newgroup", getMessage(thisSection, "group", "reset.option.create", user));
        menu.addMenuOption(thisSection.toPath()+nominateGroup, "Set existing group as safety group");
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, "group", "reset.option.back", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + join + doSuffix)
    @ResponseBody
    public Request sendJoinRequest(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        groupJoinRequestService.open(user.getUid(), groupUid, null);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "group", "join.confirm", user));
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, "group", "join.option.home", user));
        menu.addMenuOption(startMenu, getMessage(thisSection, "group", "join.option.start", user));
        menu.addMenuOption("exit", "Exit");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + recordResponse + doSuffix)
    @ResponseBody
    public Request recordResponse(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String safetyEventUid, @RequestParam("response") boolean responded) throws
            URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        //Thank the user for replying
        String prompt = (responded) ? getMessage(thisSection, "response", promptKey + ".yes", user) : getMessage(thisSection, "response", promptKey + ".no", user);
        USSDMenu menu = new USSDMenu(prompt);
        if (responded) {
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=1",
                    getMessage(thisSection, "response", "option.valid", user));
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=0",
                    getMessage(thisSection, "response", "option.invalid", user));
        } else {
            menu.addMenuOption(startMenu, "Go to main Menu");
        }
        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + recordValidity + doSuffix)
    @ResponseBody
    public Request recordValidity(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = entityUidParam) String safetyEventUid,
                                  @RequestParam("response") boolean validity) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        //only one will be recorded, the cache will be cleared for others
        safetyEventBroker.recordResponse(user.getUid(), safetyEventUid, validity);
        String prompt = getMessage(thisSection, "response", promptKey + ".yes", user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(startMenu, "Go to main Menu");

        return menuBuilder(menu);


    }


    private USSDMenu getAddressMenu(String field, User user, String fieldValue, boolean interrupted) {

        USSDMenu menu;
        if (field == null) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "house");

        } else if ("house".equals(field)) {
            addressBroker.adduserAddress(user.getUid(), fieldValue, null, null);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "street");

        } else if ("street".equals(field)) {
            addressBroker.updateUserAddress(user.getUid(), null, fieldValue, null);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "town");
        } else {
            addressBroker.updateUserAddress(user.getUid(), null, null, fieldValue);
            Address address = addressBroker.getUserAddress(user.getUid());
            String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
            String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
            menu = new USSDMenu(confirmPrompt);
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection, "address.confirm", optionsKey + ".yes", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                    getMessage(thisSection, "address.confirm", optionsKey + ".changehouse", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                    getMessage(thisSection, "address.confirm", optionsKey + ".changestreet", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                    getMessage(thisSection, "address.confirm", optionsKey + ".changearea", user));


        }
        return menu;

    }


}


