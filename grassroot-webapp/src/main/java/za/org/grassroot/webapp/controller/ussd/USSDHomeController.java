package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.enums.USSDSection.HOME;

/**
 * Controller for the USSD menu
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDHomeController extends USSDBaseController {

    private final UserResponseBroker userResponseBroker;

    // since this controller in effect routes responses, needs access to the other primary ones
    private final USSDLiveWireController liveWireController;
    private final USSDGroupController groupController;
    private final USSDVoteController voteController;
    private final USSDMeetingController meetingController;
    private final USSDTodoNewController todoController;
    private final USSDSafetyGroupController safetyController;
    private final CampaignBroker campaignBroker;

    private static final USSDSection thisSection = HOME;

    @Value("${grassroot.ussd.code.length:9}")
    private int hashPosition;

    @Value("${grassroot.ussd.safety.suffix:911}")
    private String safetyCode;

    @Value("${grassroot.ussd.sendlink.suffix:123}")
    private String sendMeLink;

    @Value("${grassroot.ussd.promotion.suffix:44}")
    private String promotionSuffix;

    @Value("${grassroot.ussd.livewire.suffix:411}")
    private String livewireSuffix;

    @Autowired
    public USSDHomeController(UserResponseBroker userResponseBroker, USSDLiveWireController liveWireController, USSDGroupController groupController, USSDVoteController voteController, USSDMeetingController meetingController, USSDTodoNewController todoController, USSDSafetyGroupController safetyController, CampaignBroker campaignBroker) {
        this.userResponseBroker = userResponseBroker;
        this.liveWireController = liveWireController;
        this.groupController = groupController;
        this.voteController = voteController;
        this.meetingController = meetingController;
        this.todoController = todoController;
        this.safetyController = safetyController;
        this.campaignBroker = campaignBroker;
    }

    @RequestMapping(value = homePath + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = userInputParam, required = false) String enteredUSSD) throws URISyntaxException {

        Long startTime = System.currentTimeMillis();

        final boolean trailingDigitsPresent = codeHasTrailingDigits(enteredUSSD);

        if (!trailingDigitsPresent && userInterrupted(inputNumber)) {
            return menuBuilder(interruptedPrompt(inputNumber));
        }

        User sessionUser = userManager.loadOrCreateUser(inputNumber);
        userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

        if (!sessionUser.isHasInitiatedSession()) {
            userManager.setHasInitiatedUssdSession(sessionUser.getUid());
        }

        USSDMenu openingMenu = trailingDigitsPresent ?
                handleResponseHasDigits(enteredUSSD, inputNumber, sessionUser) :
                checkForResponseOrDefault(sessionUser);

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
        return menuBuilder(openingMenu, true);
    }

    private USSDMenu handleResponseHasDigits(final String enteredUSSD, final String inputNumber, User user) throws URISyntaxException {
        String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
        return userInterrupted(inputNumber) && !safetyCode.equals(trailingDigits) ?
                interruptedPrompt(inputNumber) : processTrailingDigits(trailingDigits, user);
    }

    private USSDMenu checkForResponseOrDefault(final User user) throws URISyntaxException {
        EntityForUserResponse entity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), true);
        USSDResponseTypes neededResponse = neededResponse(entity, user);
        return neededResponse.equals(USSDResponseTypes.NONE)
                ? defaultStartMenu(user)
                : requestUserResponse(user, neededResponse, entity);
    }

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = homePath + startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        return menuBuilder(defaultStartMenu(userManager.loadOrCreateUser(inputNumber)));
    }

    private USSDMenu interruptedPrompt(String inputNumber) {
        String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
        log.info("The user was interrupted somewhere ...Here's the URL: " + returnUrl);

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-interrupted", user));
        promptMenu.addMenuOption(returnUrl, getMessage(thisSection, startMenu, "interrupted.resume", user));
        promptMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", user));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        userLogger.recordUssdInterruption(user.getUid(), returnUrl);
        cacheManager.clearUssdMenuForUser(inputNumber);

        return promptMenu;

    }

    private boolean userInterrupted(String inputNumber) {
        return (cacheManager.fetchUssdMenuForUser(inputNumber) != null);
    }

    private USSDResponseTypes neededResponse(EntityForUserResponse userResponse, User user) {
        return userResponse != null ? USSDResponseTypes.fromJpaEntityType(userResponse.getJpaEntityType()) :
                userManager.needsToRenameSelf(user) ? USSDResponseTypes.RENAME_SELF : USSDResponseTypes.NONE;
    }

    private USSDMenu processTrailingDigits(String trailingDigits, User user) throws URISyntaxException {
        USSDMenu returnMenu;
        log.info("Processing trailing digits ..." + trailingDigits);
        if (safetyCode.equals(trailingDigits)) {
            returnMenu = safetyController.assemblePanicButtonActivationMenu(user);
        } else if (livewireSuffix.equals(trailingDigits)) {
            returnMenu = liveWireController.assembleLiveWireOpening(user, 0);
        } else if (sendMeLink.equals(trailingDigits)) {
            returnMenu = assembleSendMeAndroidLinkMenu(user);
        } else if(isCampaignTrailingCode(trailingDigits)){
            Campaign campaign = campaignBroker.getCampaignDetailsByCode(trailingDigits);
            returnMenu = assembleCampaignMessageResponse(campaign,user);
        }  else {
            returnMenu = groupController.lookForJoinCode(user, trailingDigits);
        }
        return returnMenu;
    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private boolean isCampaignTrailingCode(String digits){
        //todo fix.
        return false;
    }

    private USSDMenu defaultStartMenu(User sessionUser) throws URISyntaxException {
        String welcomeMessage = sessionUser.hasName() ?
                getMessage(thisSection, startMenu, promptKey + "-named", sessionUser.getName(""), sessionUser) :
                getMessage(thisSection, startMenu, promptKey, sessionUser);
        return welcomeMenu(welcomeMessage, sessionUser);
    }

    private USSDMenu requestUserResponse(User user, USSDResponseTypes response, EntityForUserResponse entity) throws URISyntaxException {
        switch (response) {
            case RESPOND_SAFETY:
                return safetyController.assemblePanicButtonActivationResponse(user, (SafetyEvent) entity);
            case VOTE:
                return voteController.assembleVoteMenu(user, entity);
            case MTG_RSVP:
                return meetingController.assembleRsvpMenu(user, entity);
            case RESPOND_TODO:
                return todoController.respondToTodo(user, entity);
            case RENAME_SELF:
                return new USSDMenu(getMessage(thisSection, USSDBaseController.startMenu, promptKey + "-rename", user),
                        "rename-start");
            default:
                return defaultStartMenu(user);
        }
    }

    /*
    Section of helper methods for opening menu response handling
    todo : move these into campaign controller, as above
     */
    private USSDMenu assembleSendMeAndroidLinkMenu(User user) {
        userManager.sendAndroidLinkSms(user.getUid());
        String message = getMessage(thisSection, "link.android", promptKey, user);
        return new USSDMenu(message, optionsHomeExit(user, false));
    }

    private USSDMenu assembleCampaignMessageResponse(Campaign campaign, User user){
        Set<Locale> supportedCampaignLanguages = CampaignUtil.getCampaignSupportedLanguages(campaign);
        if(supportedCampaignLanguages.size() == 1){
            return assembleCampaignResponse(campaign,supportedCampaignLanguages.iterator().next());
        }
        if(user.getLanguageCode() != null && supportedCampaignLanguages.contains(new Locale(user.getLanguageCode()))){
            return assembleCampaignResponse(campaign,new Locale(user.getLanguageCode()));
        }
        //Discuss this with Luke. The user needs to chose a language for campaign but that language may not be their
        // preferred language
        return assembleCampaignResponseForSupportedLanguage(campaign,user);
    }

    private USSDMenu assembleCampaignResponse(Campaign campaign,Locale userLocale){
        CampaignMessage campaignMessage = CampaignUtil.getCampaignMessageByAssignmentVariationAndUserInterfaceTypeAndLocale(campaign,
                MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, userLocale);
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new HashMap<>();
        if(campaignMessage.getCampaignMessageActionSet() != null && !campaignMessage.getCampaignMessageActionSet().isEmpty()){
            for(CampaignMessageAction action : campaignMessage.getCampaignMessageActionSet()){
                String optionKey = USSDCampaignUtil.CAMPAIGN_PREFIX + action.getActionType().name().toLowerCase();
                String option  = getMessage(optionKey,userLocale.getLanguage());
                StringBuilder url = new StringBuilder();
                url.append(USSDCampaignUtil.getCampaignUrlPrefixs().get(action.getActionType()));
                url.append(USSDCampaignUtil.CODE_PARAMETER);
                url.append(campaign.getCampaignCode());
                url.append(USSDCampaignUtil.LANGUAGE_PARAMETER);
                url.append(userLocale.getLanguage());
                linksMap.put(option,url.toString());
            }
        }
        return new USSDMenu(promptMessage,linksMap);
    }

    private USSDMenu assembleCampaignResponseForSupportedLanguage(Campaign campaign, User user){
        Locale userLocale = (user.getLanguageCode() != null)? new Locale(user.getLanguageCode()):Locale.ENGLISH;
        String optionKey = USSDCampaignUtil.CAMPAIGN_PREFIX + USSDCampaignUtil.SET_LANGUAGE_URL;
        String promptMessage = getMessage(optionKey,userLocale.getLanguage());
        Map<String, String> linksMap = new HashMap<>();
        Set<Locale> localeSet = CampaignUtil.getCampaignSupportedLanguages(campaign);
        for(Locale locale : localeSet){
            String option = locale.getLanguage();
            StringBuilder url = new StringBuilder();
            url.append(USSDCampaignUtil.SET_LANGUAGE_URL);
            url.append(USSDCampaignUtil.CODE_PARAMETER);
            url.append(campaign.getCampaignCode());
            url.append(USSDCampaignUtil.LANGUAGE_PARAMETER);
            url.append(locale.getLanguage());
            linksMap.put(option,url.toString());
        }
        return new USSDMenu(promptMessage,linksMap);
    }


    /*
    Menus to process responses to votes and RSVPs,
     */
    @RequestMapping(value = homePath + U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        String errorMessage = messageAssembler.getMessage("ussd.error", "en");
        return menuBuilder(new USSDMenu(errorMessage, optionsHomeExit(userManager.findByInputNumber(inputNumber), false)));
    }

    @RequestMapping(value = homePath + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        String exitMessage = getMessage("exit." + promptKey, user);
        return menuBuilder(new USSDMenu(exitMessage));
    }

    @RequestMapping(value = homePath + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1, 1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"), true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = homePath + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return tooLongError;
    }


}