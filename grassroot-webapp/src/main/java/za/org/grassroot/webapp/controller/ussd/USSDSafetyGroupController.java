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
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

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
    private CacheUtilService cacheUtilService;


    @Autowired
    private SafetyEventLogBroker safetyEventLogBroker;

    private static final String

            createGroupMenu = "create",
            createGroupAddRespondents = "add-numbers",
            safetyGroup = "safety",
            optionsKey = "options",
            join="join-request",
            addAddress = "add-address",
            viewAddress ="view-address",
            removeAddress="remove-address",
            changeAddress ="change-address",
            unsubscribe = "unsubscribe",
            recordResponse = "record-response",
            recordValidity ="record-validity";


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
            if(!userManager.hasAddress(user.getUid())) {
                menu.addMenuOption(thisSection.toPath() + addAddress,
                        getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + ".address", user));
            }else{
                menu.addMenuOption(thisSection.toPath() + viewAddress,
                        getMessage(thisSection ,promptKey,viewAddress , user));
            }
            menu.addMenuOption(thisSection.toPath() + createGroupAddRespondents + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "add-respondents", user));
            menu.addMenuOption(thisSection.toPath() + unsubscribe + "?groupUid=" + group.getUid(), getMessage(thisSection, optionsKey, "leave", user));
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
            menu.addMenuOption(thisSection.toPath() + unsubscribe+doSuffix+"?groupUid=" + user.getSafetyGroupUid(), "Yes");
            menu.addMenuOption(thisSection.toPath() + startMenu, "No");

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
        Group group;
        USSDMenu menu;
        if (!USSDGroupUtil.isValidGroupName(groupName)) {
            menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, USSDSection.GROUP_MANAGER, createGroupMenu + doSuffix);
        } else {
            group = (interrupted)? groupBroker.load(groupUid):createGroup(user,groupName);
            String joiningCode = "*134*1994*" + group.getGroupTokenCode() + "#";
            userManager.setLastUssdMenu(user, saveGroupMenuWithInput(safetyGroup+createGroupMenu + doSuffix, group.getUid(), groupName, true));
            menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey,
                    new String[]{groupName, joiningCode}, user));
            menu.addMenuOption(groupMenuWithId(thisSection, createGroupAddRespondents, group.getUid()),
                    getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + ".numbers", user));
            if(userManager.hasAddress(user.getUid())){
                menu.addMenuOption(thisSection.toPath() + viewAddress,
                        getMessage(thisSection ,promptKey,viewAddress , user));
            }else {
                menu.addMenuOption(thisSection.toPath() + addAddress,
                        getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + ".address", user));
            }
        }
        return menuBuilder(menu);

    }


    @RequestMapping(value = safetyGroupPath + addAddress)
    @ResponseBody
    public Request addAddress(@RequestParam String msisdn,
                              @RequestParam(value = userInputParam, required = true) String fieldValue,
                              @RequestParam(value = "interrupted", required = false) boolean interrupted,
                              @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        return menuBuilder(getAddressMenu(field, user, fieldValue, false));
    }

    @RequestMapping(value = safetyGroupPath+ viewAddress)
    @ResponseBody
    public Request viewAddress(@RequestParam String msisdn) throws URISyntaxException{
        User user = userManager.findByInputNumber(msisdn);
        Address address = addressBroker.getUserAddress(user.getUid());
        String[] fields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "address.view", promptKey,fields,user));
        menu.addMenuOption(thisSection.toPath()+changeAddress+doSuffix,getMessage(thisSection,"address",optionsKey+".change",user));
        menu.addMenuOption(thisSection.toPath()+removeAddress, getMessage(thisSection,"address",optionsKey+".remove",user));

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
        }
        else if("street".equals(field)){
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


    @RequestMapping(value = safetyGroupPath + changeAddress +doSuffix)
    @ResponseBody
    public Request changeAddressDo(@RequestParam String msisdn,
                                   @RequestParam(value = userInputParam, required = true) String fieldValue,
                                   @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                   @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu;

        if ("house".equals(field)) {
            addressBroker.updateHouseNumber(user.getUid(), fieldValue);
        } else if ("street".equals(field)) {
            addressBroker.updateStreet(user.getUid(), fieldValue);
        } else if ("town".equals(field)) {
            addressBroker.updateTown(user.getUid(), fieldValue);
        }
            Address address = addressBroker.getUserAddress(user.getUid());
            String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
            String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
            menu = new USSDMenu(confirmPrompt);
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection, "address.confirm" ,optionsKey + ".yes", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-house", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-street", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-area", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + removeAddress)
    @ResponseBody

    //todo consider moving all address handlers to the user controller
    public Request removeAddress(@RequestParam String msisdn) throws URISyntaxException{
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(getMessage(thisSection,"address.remove",promptKey,user));
        menu.addMenuOption(thisSection.toPath() +removeAddress+doSuffix, getMessage(thisSection,"address.remove",optionsKey+".yes", user));
        menu.addMenuOption(thisSection.toPath()+viewAddress,  getMessage(thisSection,"address.remove",optionsKey+".no", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath+removeAddress+doSuffix)
    @ResponseBody
    public Request removeAddressDo(@RequestParam String msisdn) throws Exception{
        User user = userManager.findByInputNumber(msisdn);
        addressBroker.removeAddress(user.getUid());
        USSDMenu menu = new USSDMenu(getMessage(thisSection,"address.remove.confirm",promptKey,user));
        menu.addMenuOption(thisSection.toPath()+addAddress, "Add address");
        menu.addMenuOption(thisSection.toPath()+startMenu, "Go to safety group menu");

        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + createGroupAddRespondents)
    @ResponseBody
    public Request createGroupAddNumbersOpeningPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddRespondents, groupUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, createGroupAddRespondents, promptKey, user),
                groupMenuWithId(thisSection, createGroupAddRespondents + doSuffix, groupUid)));
    }

    @RequestMapping(value = safetyGroupPath + createGroupAddRespondents + doSuffix)
    @ResponseBody
    public Request addRespondentsToGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                         @RequestParam(value = groupUidParam, required = true) String groupUid,
                                         @RequestParam(value = userInputParam, required = true) String userInput,
                                         @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException{

        USSDMenu menu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithInput(safetyGroup+ createGroupAddRespondents + doSuffix, groupUid, userResponse, false));

        if (!userResponse.trim().equals("0")) {
            menu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
                    userResponse, createGroupAddRespondents + doSuffix);
        } else {
            Group group = groupBroker.load(groupUid);
            Set<User> respondents = group.getMembers();
            for (User respondent : respondents) {
                if (!respondent.hasSafetyGroup()) {
                    userManager.setSafetyGroup(respondent.getUid(), group.getUid());
                }
            }
            menu = new USSDMenu(getMessage(thisSection,promptKey,"respondents.confirm", user));
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection, createGroupAddRespondents, optionsKey + ".home", user));
            menu.addMenuOption(startMenu,
                    getMessage(thisSection, createGroupAddRespondents, optionsKey + ".home", user));
        }

        return menuBuilder(menu);
    }


    @RequestMapping(value = safetyGroupPath + unsubscribe)
    @ResponseBody
    public Request unsubscribe(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);

        USSDMenu menu = new USSDMenu(getMessage(thisSection,"group",promptKey+".unsubscribe",user));
        menu.addMenuOption(thisSection.toPath() + "unsubscribe-do?groupUid=" + groupUid, "Yes");
        menu.addMenuOption(thisSection.toPath() + startMenu, "Back");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + unsubscribe + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        groupBroker.unsubscribeMember(user.getUid(), groupUid);
        userManager.setSafetyGroup(user.getUid(), null);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "group", "unsubscribe.confirm", user));
        menu.addMenuOption(thisSection.toPath() + createGroupMenu, getMessage(thisSection,"group","unsubscribe.option.create", user));
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection,"group","unsubscribe.option.back", user));

        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + join + doSuffix)
    @ResponseBody
    public Request sendJoinRequest(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        groupJoinRequestService.open(user.getUid(), groupUid, null);
        USSDMenu menu = new USSDMenu(getMessage(thisSection,"group","join.confirm",user));
        menu.addMenuOption(USSDSection.GROUP_MANAGER.toPath() + startMenu, getMessage(thisSection,"group","join.option.home", user));
        menu.addMenuOption(startMenu, getMessage(thisSection,"group","join.option.start", user));
        menu.addMenuOption("exit", "Exit");


        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + recordResponse+ doSuffix)
    @ResponseBody
    public Request recordResponse(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String safetyEventUid, @RequestParam("response") boolean responded) throws
            URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        safetyEventLogBroker.recordResponse(user.getUid(), safetyEventUid, SafetyEventLogType.RESPONSE, responded);
        String prompt = (responded) ? getMessage(thisSection, "response",promptKey+".yes",user) : getMessage(thisSection,"response",promptKey+".no", user);
        USSDMenu menu = new USSDMenu(prompt);
        if (responded) {
            safetyEventBroker.deactivate(safetyEventUid);
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=true",
                    getMessage(thisSection, "response","option.valid",user));
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=false",
                    getMessage(thisSection, "response","option.invalid",user));
        } else {
            menu.addMenuOption(startMenu, "Go to main Menu");
        }
        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + recordValidity+doSuffix)
    @ResponseBody
    public Request recordValidity(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = entityUidParam) String safetyEventUid,
                                  @RequestParam("response") String validity) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        SafetyEvent safetyEvent = safetyEventBroker.load(safetyEventUid);
        safetyEventLogBroker.recordValidity(user.getUid(), safetyEvent.getUid(), validity);
        String prompt = getMessage(thisSection, "response",promptKey+".yes",user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(startMenu, "Go to main Menu");

        return menuBuilder(menu);


    }

    private Group createGroup(User user, String groupName ){
        MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
       Group group = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(creator),
                GroupPermissionTemplate.DEFAULT_GROUP, null, null, true);
        if (group != null) {
            groupBroker.makeSafetyGroup(user.getUid(), group.getUid());
        }

        return group;

    }

    private USSDMenu getAddressMenu(String field, User user, String fieldValue, boolean interrupted) {

        USSDMenu menu;

        if (field == null) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "house");

        } else if ("house".equals(field)) {
            addressBroker.adduserAddress(user.getUid(),fieldValue, null, null);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "street");

        } else if ("street".equals(field)) {
            addressBroker.updateStreet(user.getUid(), fieldValue);
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "town");
        } else {
            addressBroker.updateTown(user.getUid(), fieldValue);
            Address address = addressBroker.getUserAddress(user.getUid());
            String[] confirmFields = new String[]{address.getHouseNumber(), address.getStreetName(), address.getTown()};
            String confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
            menu = new USSDMenu(confirmPrompt);
            menu.addMenuOption(thisSection.toPath() + startMenu,
                    getMessage(thisSection,"address.confirm", optionsKey + ".yes", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=house",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-house", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=street",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-street", user));
            menu.addMenuOption(thisSection.toPath() + "change-address?&field=town",
                    getMessage(thisSection,"address.confirm",optionsKey + ".change-area", user));


        }
        return menu;

    }



}


