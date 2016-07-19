package za.org.grassroot.webapp.controller.ussd;

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
import za.org.grassroot.core.enums.SafetyEventLogType;
import za.org.grassroot.services.AddressBroker;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.SafetyEventLogBroker;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Set;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.groupMenuWithId;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveGroupMenu;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveGroupMenuWithInput;

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


    @Autowired
    private SafetyEventLogBroker safetyEventLogBroker;

    private static final String

            createGroupMenu = "create",
            createGroupAddNumbers = "add-numbers",
            safetyGroup = "safety",
            optionsKey = "options";


    private static final String safetyGroupPath = homePath + safetyGroup + "/";
    private static final USSDSection thisSection = USSDSection.SAFETY_GROUP_MANAGER;
    private static final String groupUidParam = "groupUid";


    @RequestMapping(value = safetyGroupPath + startMenu)
    @ResponseBody
    public Request manageSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if (user.hasSafetyGroup()) {
            Group group = groupBroker.load(user.getSafetyGroupUid());
            String joiningCode = "*134*1994*" + group.getGroupTokenCode() + "#";
            menu = new USSDMenu(getMessage(thisSection, promptKey, "exists", joiningCode, user));
            menu.addMenuOption(thisSection.toPath() + "add-address", getMessage(thisSection, optionsKey, "add-address", user));
            menu.addMenuOption(thisSection.toPath() + createGroupAddNumbers + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "add-respondents", user));
            menu.addMenuOption(thisSection.toPath() + "unsubscribe" + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "leave", user));
            menu.addMenuOption(thisSection.toPath() + createGroupMenu, getMessage(thisSection, optionsKey, "create", user));
            menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection, optionsKey, "back", user));

        } else {
            menu = new USSDMenu(getMessage(thisSection, promptKey, "not-exist", user));
            menu.addMenuOption(thisSection.toPath() + createGroupMenu, getMessage(thisSection, optionsKey, "create-yes", user));
            menu.addMenuOption(thisSection.toPath() + startMenu, getMessage(thisSection, optionsKey, "create-no", user));
        }

        return menuBuilder(menu);

    }


    @RequestMapping(value = safetyGroupPath + createGroupMenu)
    @ResponseBody
    public Request createSafetyGroup(@RequestParam String msisdn,
                                     @RequestParam(value = userInputParam, required = true) String groupName,
                                     @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                     @RequestParam(value = groupUidParam, required = false) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if (user.hasSafetyGroup()) {
            menu = new USSDMenu(getMessage(thisSection, promptKey, "hasgroup", user));
            menu.addMenuOption(thisSection.toPath() + "unsubscribe-do?groupUid=" + groupUid, "Yes");
            menu.addMenuOption(thisSection.toPath() + startMenu, "Back");

        } else {
            menu = ussdGroupUtil.createGroupPrompt(user, thisSection, createGroupMenu + doSuffix);
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + createGroupMenu + doSuffix)
    @ResponseBody
    public Request createSafetyGroupWithName(@RequestParam String msisdn,
                                             @RequestParam(value = userInputParam) String groupName,
                                             @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                             @RequestParam(value = groupUidParam, required = false) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        Group createdGroup;
        USSDMenu menu;
        if (!USSDGroupUtil.isValidGroupName(groupName)) {
            userManager.setLastUssdMenu(user, groupMenus + safetyGroupPath + createGroupMenu);
            menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, USSDSection.GROUP_MANAGER, createGroupMenu + doSuffix);
        } else {
            if (interrupted) {
                createdGroup = groupBroker.load(groupUid);
            } else {
                MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
                createdGroup = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(creator),
                        GroupPermissionTemplate.DEFAULT_GROUP, null, null, true);
                if (createdGroup != null) {
                    groupBroker.makeSafetyGroup(user.getUid(), createdGroup.getUid());
                }
            }
            String joiningCode = "*134*1994*" + createdGroup.getGroupTokenCode() + "#";
            userManager.setLastUssdMenu(user, saveGroupMenuWithInput(createGroupMenu + doSuffix, createdGroup.getUid(), groupName));
            menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey,
                    new String[]{groupName, joiningCode}, user));

            menu.addMenuOption(groupMenuWithId(thisSection, createGroupAddNumbers, createdGroup.getUid()),
                    getMessage(thisSection, createGroupMenu + doSuffix, super.optionsKey + "numbers", user));
            menu.addMenuOption(thisSection.toPath() + "add-address",
                    getMessage(thisSection, createGroupMenu + doSuffix, super.optionsKey + "address", user));


        }
        return menuBuilder(menu);

    }


    @RequestMapping(value = safetyGroupPath + "add-address")
    @ResponseBody
    public Request addAddress(@RequestParam String msisdn,
                              @RequestParam(value = userInputParam, required = true) String value,
                              @RequestParam(value = "interrupted", required = false) boolean interrupted,
                              @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        return menuBuilder(getAddressMenu(field, user, value));
    }

    @RequestMapping(value = safetyGroupPath + "change-address")
    @ResponseBody
    public Request changeAddress(@RequestParam String msisdn,
                                 @RequestParam(value = userInputParam, required = true) String value,
                                 @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                 @RequestParam(value = "field", required = true) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if (field == null) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "house");

        } else if ("house".equals(field)) {
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


    @RequestMapping(value = safetyGroupPath + "change-address-do")
    @ResponseBody
    public Request changeAddressDo(@RequestParam String msisdn,
                                   @RequestParam(value = userInputParam, required = true) String value,
                                   @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                   @RequestParam(value = "field", required = true) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;
        if ("home".equals(field)) {
            addressBroker.updateHouseNumber(user.getUid(), value);
        } else if ("street".equals(field)) {
            addressBroker.updateStreet(user.getUid(), value);
        } else if ("town".equals(field)) {
            addressBroker.updateTown(user.getUid(), value);
        }
        Address address = addressBroker.getUserAddress(user.getUid());
        String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
        String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
        menu = new USSDMenu(confirmPrompt);
        menu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "yes", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-house", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-street", user));
        menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-area", user));

        return menuBuilder(menu);


    }

    @RequestMapping(value = safetyGroupPath + createGroupAddNumbers)
    @ResponseBody
    public Request createGroupAddNumbersOpeningPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddNumbers, groupUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, createGroupAddNumbers, promptKey, user),
                groupMenuWithId(thisSection, createGroupAddNumbers + doSuffix, groupUid)));
    }

    @RequestMapping(value = safetyGroupPath + createGroupAddNumbers + doSuffix)
    @ResponseBody
    public Request addNumbersToGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                     @RequestParam(value = groupUidParam, required = true) String groupUid,
                                     @RequestParam(value = userInputParam, required = true) String userInput,
                                     @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        USSDMenu menu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithInput(createGroupAddNumbers + doSuffix, groupUid, userResponse));

        if (!userResponse.trim().equals("0")) {
            menu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
                    userResponse, createGroupAddNumbers + doSuffix);
        } else {
            Group group = groupBroker.load(groupUid);
            Set<User> respondents = group.getMembers();
            for (User respondent : respondents) {
                userManager.setSafetyGroup(respondent.getUid(), group.getUid());
            }
            menu = new USSDMenu("Respondents added");
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection, createGroupAddNumbers, super.optionsKey + "home", user));
        }

        return menuBuilder(menu);
    }


    @RequestMapping(value = safetyGroupPath + "unsubscribe")
    @ResponseBody
    public Request unsubscribe(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);

        USSDMenu menu = new USSDMenu("Are you sure you want to leave the safety group?");
        menu.addMenuOption(thisSection.toPath() + "unsubscribe-do?groupUid=" + groupUid, "Yes");
        menu.addMenuOption(thisSection.toPath() + startMenu, "Back");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + "unsubscribe" + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        groupBroker.unsubscribeMember(user.getUid(), groupUid);
        userManager.setSafetyGroup(user.getUid(), null);

        USSDMenu menu = new USSDMenu("You have been unsubscribed from the safety group. What next?");
        menu.addMenuOption(thisSection.toPath() + createGroupMenu, "Create safety group");
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, "Go back to group menu");

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + "join-request" + doSuffix)
    @ResponseBody
    public Request sendJoinRequest(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        groupJoinRequestService.open(user.getUid(), groupUid, null);
        USSDMenu menu = new USSDMenu("Join request sent. What do you want to do?");
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, "Go to group menu");
        menu.addMenuOption(USSDSection.BASE.toPath()+startMenu, "Go to main menu");
        menu.addMenuOption("exit url", "Exit");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + "record-response" +doSuffix)
    @ResponseBody
    public Request recordResponse(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = entityUidParam) String safetyEventUid, @RequestParam("response") boolean responded) throws
            URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        safetyEventLogBroker.recordResponse(user.getUid(), safetyEventUid, SafetyEventLogType.RESPONSE,responded);
        String prompt =(responded)?"Was it a real emergency?":"Thank you, your response was recorded. What do want to do?";
        USSDMenu menu = new USSDMenu(prompt);
        if(responded){
            menu.addMenuOption(thisSection.toPath()+"record-validity-do?&entityUid=" +safetyEventUid+ "&response=true", "Yes, it was a real emergency?");
            menu.addMenuOption(thisSection.toPath()+"record-validity-do?&entityUid="+safetyEventUid+"&response=false", "No it was a false alarm");
        }else{
            menu.addMenuOption(USSDSection.BASE.toPath() +startMenu, "Go to main Menu");
        }
        return  menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath +"record-validity-do")
    @ResponseBody
    public Request recordValidity(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value =entityUidParam) String safetyEventUid,
                                  @RequestParam("response") String validity) throws URISyntaxException{

        User user = userManager.findByInputNumber(inputNumber);
        SafetyEvent safetyEvent = safetyEventBroker.load(safetyEventUid);
        safetyEventLogBroker.recordValidity(user.getUid(),safetyEvent.getUid(),validity);
        String prompt = "Thank you for attending to the the emergency call. What would you like to do next?";

        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(USSDSection.BASE.toPath() +startMenu, "Go to main Menu");
        menu.addMenuOption("exit url", "Exit");

        return menuBuilder(menu);


    }

    private USSDMenu getAddressMenu(String field, User user, String value) {

        USSDMenu menu;
        if (field == null) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "house");

        } else if ("house".equals(field)) {
            addressBroker.adduserAddress(user.getUid(), value, null, null);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "street");

        } else if ("street".equals(field)) {
            addressBroker.updateStreet(user.getUid(), value);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "town");
        } else {
            addressBroker.updateTown(user.getUid(), value);
            Address address = addressBroker.getUserAddress(user.getUid());
            String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
            String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
            menu = new USSDMenu(confirmPrompt);
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "yes", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                    getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-house", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                    getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-street", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                    getMessage(thisSection + "." + "address.confirm" + "." + super.optionsKey + "change-area", user));


        }
        return menu;

    }


}


