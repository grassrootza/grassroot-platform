package za.org.grassroot.webapp.controller.ussd;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignTextBroker;
import za.org.grassroot.webapp.controller.ussd.group.USSDGroupJoinController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;

import javax.annotation.PostConstruct;
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

    // since this controller in effect routes responses, needs access to the other primary ones
    // setters are for testing (since we need this controller in the tests of the handler)
    private final USSDLiveWireController liveWireController;
    private final USSDGroupJoinController groupJoinController;
    @Setter(AccessLevel.PACKAGE) private USSDVoteController voteController;
    @Setter(AccessLevel.PACKAGE) private USSDMeetingController meetingController;
    private final USSDTodoController todoController;
    private final USSDSafetyGroupController safetyController;
    private USSDGeoApiController geoApiController;

    private final CampaignBroker campaignBroker;
    private final UserResponseBroker userResponseBroker;
    private CampaignTextBroker campaignTextBroker;
    private LocationInfoBroker locationInfoBroker;

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

    @Value("${grassroot.geo.apis.enabled:false}")
    private boolean geoApisEnabled;

    private Map<String, String> geoApiSuffixes;

    @Autowired
    public USSDHomeController(UserResponseBroker userResponseBroker, USSDLiveWireController liveWireController, USSDGroupJoinController groupJoinController, USSDVoteController voteController, USSDMeetingController meetingController, USSDTodoController todoController, USSDSafetyGroupController safetyController, CampaignBroker campaignBroker) {
        this.userResponseBroker = userResponseBroker;
        this.liveWireController = liveWireController;
        this.groupJoinController = groupJoinController;
        this.voteController = voteController;
        this.meetingController = meetingController;
        this.todoController = todoController;
        this.safetyController = safetyController;
        this.campaignBroker = campaignBroker;
    }

    @Autowired(required = false)
    public void setGeoApiController(USSDGeoApiController ussdGeoApiController) {
        this.geoApiController = ussdGeoApiController;
    }

    @Autowired(required = false) // may turn this off / on in future
    public void setCampaignTextBroker(CampaignTextBroker campaignTextBroker) {
        this.campaignTextBroker = campaignTextBroker;
    }

    @Autowired(required = false)
    public void setLocationInfoBroker(LocationInfoBroker locationInfoBroker) {
        this.locationInfoBroker = locationInfoBroker;
    }

    @PostConstruct
    public void init() {
        if (locationInfoBroker != null) {
            log.info("Initiating USSD, setting geo apis");
            geoApiSuffixes = locationInfoBroker.getAvailableSuffixes();
            log.info("Set geo api suffixes: {}", geoApiSuffixes);
        } else {
            log.info("Geo APIs disabled, not setting");
        }
    }

    @RequestMapping(value = homePath + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = userInputParam, required = false) String enteredUSSD) throws URISyntaxException {

        Long startTime = System.currentTimeMillis();

        final boolean trailingDigitsPresent = codeHasTrailingDigits(enteredUSSD);
        log.info("Initiating USSD, trailing digits present: {}", trailingDigitsPresent);

        if (!trailingDigitsPresent && userInterrupted(inputNumber)) {
            return menuBuilder(interruptedPrompt(inputNumber, null));
        }

        User sessionUser = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
        userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

        USSDMenu openingMenu = trailingDigitsPresent ?
                handleTrailingDigits(enteredUSSD, inputNumber, sessionUser) :
                checkForResponseOrDefault(sessionUser);

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
        return menuBuilder(openingMenu, true);
    }

    private USSDMenu handleTrailingDigits(final String enteredUSSD, final String inputNumber, User user) {
        String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
        return userInterrupted(inputNumber) && !safetyCode.equals(trailingDigits) ?
                interruptedPrompt(inputNumber, trailingDigits) : directBasedOnTrailingDigits(trailingDigits, user);
    }

    private USSDMenu checkForResponseOrDefault(final User user) throws URISyntaxException {
        recordInitiatedAndSendWelcome(user, true);
        EntityForUserResponse entity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), true);
        USSDResponseTypes neededResponse = neededResponse(entity, user);
        return neededResponse.equals(USSDResponseTypes.NONE)
                ? defaultStartMenu(user)
                : requestUserResponse(user, neededResponse, entity);
    }

    private void recordInitiatedAndSendWelcome(User user, boolean sendWelcome) {
        if (!user.isHasInitiatedSession()) {
            userManager.setHasInitiatedUssdSession(user.getUid(), sendWelcome);
        }
    }

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = homePath + startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(required = false) String trailingDigits) throws URISyntaxException {
        final User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
        return menuBuilder(trailingDigits != null ?
                directBasedOnTrailingDigits(trailingDigits, user) :  defaultStartMenu(user));
    }

    private USSDMenu interruptedPrompt(String inputNumber, String trailingDigits) {
        String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
        log.info("The user was interrupted somewhere: trailing digits: {}, URL: {}", trailingDigits, returnUrl);

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-interrupted", user));
        promptMenu.addMenuOption(returnUrl, getMessage(thisSection, startMenu, "interrupted.resume", user));

        final String startMenuOption = startMenu + "_force" + (!StringUtils.isEmpty(trailingDigits) ? "?trailingDigits=" + trailingDigits : "");
        log.info("User interrupted, start menu option: {}", startMenuOption);
        promptMenu.addMenuOption(startMenuOption, getMessage(thisSection, startMenu, "interrupted.start", user));

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
                userManager.needsToSetName(user, false) ? USSDResponseTypes.RENAME_SELF : USSDResponseTypes.NONE;
    }

    private USSDMenu directBasedOnTrailingDigits(String trailingDigits, User user) {
        USSDMenu returnMenu;
        log.info("Processing trailing digits ..." + trailingDigits);
        boolean sendWelcomeIfNew = false;
        if (safetyCode.equals(trailingDigits)) {
            returnMenu = safetyController.assemblePanicButtonActivationMenu(user);
        } else if (livewireSuffix.equals(trailingDigits)) {
            returnMenu = liveWireController.assembleLiveWireOpening(user, 0);
            sendWelcomeIfNew = true;
        } else if (sendMeLink.equals(trailingDigits)) {
            returnMenu = assembleSendMeAndroidLinkMenu(user);
            sendWelcomeIfNew = true;
        } else if (geoApisEnabled && geoApiSuffixes.keySet().contains(trailingDigits)) {
            returnMenu = geoApiController.openingMenu(user, geoApiSuffixes.get(trailingDigits));
            sendWelcomeIfNew = false;
        } else {
            returnMenu = groupJoinController.lookForJoinCode(user, trailingDigits);
            boolean groupJoin = returnMenu != null;
            if (!groupJoin) {
                log.info("checking if campaign: {}", trailingDigits);
                returnMenu = getActiveCampaignForTrailingCode(trailingDigits, user);
            }
            sendWelcomeIfNew = groupJoin;
            log.info("group or campaign join, trailing digits ={}, send welcome = {}", trailingDigits, sendWelcomeIfNew);
        }
        recordInitiatedAndSendWelcome(user, sendWelcomeIfNew);
        return returnMenu;
    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        log.info("entered USSD = {}, hashPosition = {}", enteredUSSD, hashPosition);
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private USSDMenu getActiveCampaignForTrailingCode(String trailingDigits, User user){
        Campaign campaign = campaignBroker.getCampaignDetailsByCode(trailingDigits, user.getUid(), true, UserInterfaceType.USSD);
        log.info("found a campaign? : {}", campaign);
        return (campaign != null) ?
                assembleCampaignMessageResponse(campaign, user):
                welcomeMenu(getMessage(HOME, startMenu, promptKey + ".unknown.request", user), user);
    }

    private USSDMenu defaultStartMenu(User sessionUser) {
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
     */
    private USSDMenu assembleSendMeAndroidLinkMenu(User user) {
        userManager.sendAndroidLinkSms(user.getUid());
        String message = getMessage(thisSection, "link.android", promptKey, user);
        return new USSDMenu(message, optionsHomeExit(user, false));
    }

    private USSDMenu assembleCampaignMessageResponse(Campaign campaign, User user) {
        log.info("fire off SMS in background, if exists ...");
        campaignTextBroker.checkForAndTriggerCampaignText(campaign.getUid(), user.getUid(), null, UserInterfaceType.USSD);
        log.info("fired off ... continue ...");
        Set<Locale> supportedCampaignLanguages = campaignBroker.getCampaignLanguages(campaign.getUid());
        userLogger.recordUserLog(user.getUid(), UserLogType.CAMPAIGN_ENGAGED, campaign.getUid(), UserInterfaceType.USSD);
        if(supportedCampaignLanguages.size() == 1) {
            return assembleCampaignResponse(campaign, supportedCampaignLanguages.iterator().next());
        } else if(!StringUtils.isEmpty(user.getLanguageCode()) && supportedCampaignLanguages.contains(new Locale(user.getLanguageCode()))){
            return assembleCampaignResponse(campaign, user.getLocale());
        } else {
            return assembleCampaignResponseForSupportedLanguage(campaign, user);
        }
    }

    private USSDMenu assembleCampaignResponse(Campaign campaign, Locale userLocale) {
        CampaignMessage campaignMessage = campaignBroker.getOpeningMessage(campaign.getUid(), userLocale, UserInterfaceType.USSD, null);
        String promptMessage = campaignMessage.getMessage();
        Map<String, String> linksMap = new HashMap<>();
        if (campaignMessage.getNextMessages() != null && !campaignMessage.getNextMessages().isEmpty()){
            for(Map.Entry<String, CampaignActionType> action : campaignMessage.getNextMessages().entrySet()){
                String optionKey = USSDCampaignConstants.CAMPAIGN_PREFIX + action.getValue().name().toLowerCase();
                String option = getMessage(optionKey, userLocale.getLanguage());
                StringBuilder url = new StringBuilder("campaign/");
                url.append(USSDCampaignConstants.getCampaignUrlPrefixs().get(action.getValue())).append("?");
                url.append(USSDCampaignConstants.MESSAGE_UID_PARAMETER).append(action.getKey());
                log.info("adding url: {}", url.toString());
                linksMap.put(url.toString(), option);
            }
        }
        return new USSDMenu(promptMessage,linksMap);
    }

    private USSDMenu assembleCampaignResponseForSupportedLanguage(Campaign campaign, User user) {
        String promptMessage = getMessage("user.language.prompt", user.getLocale().getLanguage());
        Map<String, String> linksMap = new HashMap<>();
        Set<Locale> localeSet = campaignBroker.getCampaignLanguages(campaign.getUid());
        for(Locale locale : localeSet) {
            String option = getMessage("language." + locale.getLanguage(), user.getLocale().getLanguage());
            String url = "campaign/set-lang?campaignUid=" + campaign.getUid() +
                    USSDCampaignConstants.LANG_SUFFIX + locale.getLanguage();
            linksMap.put(url, option);
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