package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;
import za.org.grassroot.language.DateTimeParseFailure;
import za.org.grassroot.services.EventLogBroker;
import za.org.grassroot.services.EventRequestBroker;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDMeetingController extends USSDController {


    @Autowired
    private USSDEventUtil eventUtil;

    @Autowired
    private EventRequestBroker eventRequestBroker;

    @Autowired
    private EventLogBroker eventLogBroker;

    @Autowired
    private EventLogRepository eventLogRepository;

    private Logger log = LoggerFactory.getLogger(getClass());
    private static final String path = homePath + meetingMenus;
    private static final USSDSection thisSection = USSDSection.MEETINGS;

    private static final String
            newGroupMenu = "newgroup",
            groupName = "groupName",
            groupHandlingMenu = "group",
            subjectMenu = "subject",
            timeMenu = "time",
            placeMenu = "place",
            confirmMenu = "confirm",
            timeOnly = "time_only",
            dateOnly = "date_only",
            send = "send";

    private static final String
            manageMeetingMenu = "manage",
            viewMeetingDetails = "details",
            changeDateAndTime = "changeDateTime",
            changeMeetingLocation = "changeLocation",
            cancelMeeting = "cancel",
            newTime = "new_time",
            newDate = "new_date",
            modifyConfirm = "modify";

    private static final String requestUidParam = "requestUid";

    private static final List<String> menuSequence =
            Arrays.asList(startMenu, subjectMenu, placeMenu, timeMenu, confirmMenu, send);

    private String nextMenu(String currentMenu) {
        return menuSequence.get(menuSequence.indexOf(currentMenu) + 1);
    }

    // for stubbing with Mockito ...
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    /*
    Opening menu. Check if the user has created meetings which are upcoming, and, if so, give options to view and
    manage those. If not, initialize an event and ask them to pick a group or create a new one.
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request meetingOrg(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                              @RequestParam(value = "newMtg", required = false) boolean newMeeting) throws URISyntaxException, IOException {

        User user;

        try {
            user = userManager.findByInputNumber(inputNumber);
        } catch (NoSuchElementException e) {
            return noUserError;
        }

        USSDMenu returnMenu;

        if (newMeeting || !eventManager.userHasEventsToView(user, EventType.MEETING, true)) {
            returnMenu = ussdGroupUtil.askForGroup(user, thisSection, subjectMenu, newGroupMenu, groupName, true);
        } else {
            String prompt = getMessage(thisSection, startMenu, promptKey + ".new-old", user);
            String newOption = getMessage(thisSection, startMenu, optionsKey + "new", user);
            returnMenu = eventUtil.listUpcomingEvents(user, thisSection, prompt, manageMeetingMenu, true, startMenu + "?newMtg=1", newOption);
        }

        log.info("Timing a core menu ... meeting start menu handing over to menu builder ...");
        return menuBuilder(returnMenu);
    }

    /**
     * SECTION: Methods and menus for creating a new meeting follow after this
     *
     * First, the group handling menus, if the user chose to create a new group, or had no groups, they will go
     * through the first two menus, which handle number entry and group creation; if they chose an existing group, they
     * go to the subject input menu. We create the event entity once the group has been completed/selected.
     */

    @RequestMapping(value = path + newGroupMenu)
    @ResponseBody
    public Request newGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, meetingMenus + newGroupMenu);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(user, thisSection, "groupName"));
    }


    @RequestMapping(value = path + groupName)
    @ResponseBody
    public Request createGroupSetName(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                      @RequestParam(value = userInputParam, required = false) String userResponse
                         ) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (!USSDGroupUtil.isValidGroupName(userResponse) ){
          return  menuBuilder(ussdGroupUtil.invalidGroupNamePrompt(user, userResponse, thisSection, newGroupMenu));
        } else {
            Set<MembershipInfo> members = Sets.newHashSet(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
            Group group = groupBroker.create(user.getUid(), userResponse, null, members, GroupPermissionTemplate.DEFAULT_GROUP, null, null, true);
            return menuBuilder(ussdGroupUtil.addNumbersToGroupPrompt(user, group, thisSection, groupHandlingMenu));
    }
    }

    /*
     The most complex handler in this class, as we have four cases, depending on two independent variables:
     a) whether the user has signalled to continue adding numbers or to stop (userResponse.equals('0'))
     b) whether we have a valid group yet or not (groupId is not null)
     the complexity for continuing to add numbers is in the Util class; for stopping, we have to handle here as it
     is specific to this section of menus. Needing to deal with interruptions adds a final layers of complexity.
     Note: if user input is '0' with a valid group, then we mimic the set subject menu and create an event
    */

    @RequestMapping(value = path + groupHandlingMenu)
    @ResponseBody
    public Request createGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = false) String groupUid,
                               @RequestParam(value = userInputParam, required = false) String userResponse,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        USSDMenu thisMenu;

        // if priorInput exists, we have been interrupted, so use that as userInput, else use what is passed as 'request'
        String userInput = (priorInput != null) ? USSDUrlUtil.decodeParameter(priorInput) : userResponse;
        String includeGroup = (groupUid != null && !groupUid.equals("")) ? groupUidUrlSuffix + groupUid : ""; // there is no case where eventId is not null but groupId is

        String urlToSave = USSDUrlUtil.saveMenuUrlWithInput(thisSection, groupHandlingMenu, includeGroup, userInput);
        log.info("In group handling menu, have saved this URL: " + urlToSave);
        User user = userManager.findByInputNumber(inputNumber, urlToSave);

        if (!userInput.trim().equals("0")) {
            thisMenu = ussdGroupUtil.addNumbersToExistingGroup(
                        user, groupUid, USSDSection.MEETINGS, userInput, groupHandlingMenu);
        } else {
            thisMenu = new USSDMenu(true);
            if (groupUid == null) {
                thisMenu.setPromptMessage(getMessage(thisSection, groupHandlingMenu, promptKey + ".no-group", user));
                thisMenu.setNextURI(meetingMenus + groupHandlingMenu);
            } else {
                MeetingRequest meetingRequest = eventRequestBroker.createEmptyMeetingRequest(user.getUid(), groupUid);
                String mtgRequestUid = meetingRequest.getUid();
                thisMenu.setPromptMessage(getMessage(thisSection, nextMenu(startMenu), promptKey, user));
                thisMenu.setNextURI(meetingMenus + nextMenu(nextMenu(startMenu)) + entityUidUrlSuffix + mtgRequestUid
                        + "&" + previousMenu + "=" + nextMenu(startMenu));
            }
        }

        return menuBuilder(thisMenu);

    }

    /*
    The subsequent menus are more straightforward, each filling in a part of the event data structure
     */

    // NOTE: Whatever menu comes after the group selection _has_ to use "groupId" (groupIdParam) instead of "request" (userInputParam),
    // because the menu response will overwrite the 'request' parameter in the returned URL, and we will get a fail on the group
    // note: this is also where we create the event request, as after this we need to keep and store

    @RequestMapping(value = path + subjectMenu)
    @ResponseBody
    public Request getSubject(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                              @RequestParam(value = entityUidParam, required = false) String mtgRequestUid,
                              @RequestParam(value = groupUidParam, required = false) String groupUid,
                              @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                              @RequestParam(value = "revising", required = false) boolean revising) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);

        if (!interrupted && !revising) {
            MeetingRequest meetingRequest = eventRequestBroker.createEmptyMeetingRequest(sessionUser.getUid(), groupUid);
            mtgRequestUid = meetingRequest.getUid();
        }

        cacheManager.putUssdMenuForUser(inputNumber, saveMeetingMenu(subjectMenu, mtgRequestUid, revising));
        String promptMessage = getMessage(thisSection, subjectMenu, promptKey, sessionUser);
        String nextUrl = (!revising) ? nextUrl(subjectMenu, mtgRequestUid) : confirmUrl(subjectMenu, mtgRequestUid);
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));
    }

    @RequestMapping(value = path + placeMenu)
    @ResponseBody
    public Request getPlace(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam(value = entityUidParam) String mtgRequestUid,
                            @RequestParam(value = previousMenu, required = false) String priorMenu,
                            @RequestParam(value = userInputParam) String userInput,
                            @RequestParam(value = "interrupted", required = false) boolean interrupted,
                            @RequestParam(value = "revising", required = false) boolean revising) throws URISyntaxException {

        // todo: add error and exception handling

        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(placeMenu, mtgRequestUid, revising));
        if (!interrupted) eventUtil.updateEventRequest(user.getUid(), mtgRequestUid, priorMenu, userInput);

        String promptMessage = getMessage(thisSection, placeMenu, promptKey, user);
        String nextUrl = (!revising) ? nextUrl(placeMenu, mtgRequestUid) : confirmUrl(placeMenu, mtgRequestUid);
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));
    }

    @RequestMapping(value = path + timeMenu)
    @ResponseBody
    public Request getTime(@RequestParam(value = phoneNumber) String inputNumber,
                           @RequestParam(value = entityUidParam) String mtgRequestUid,
                           @RequestParam(value = previousMenu, required = false) String priorMenu,
                           @RequestParam(value = userInputParam, required = false) String userInput,
                           @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeMenu, mtgRequestUid, false));
        if (!interrupted) eventUtil.updateEventRequest(sessionUser.getUid(), mtgRequestUid, priorMenu, userInput);

        String promptMessage = getMessage(thisSection, timeMenu, promptKey, sessionUser);
        String nextUrl = meetingMenus + nextMenu(timeMenu) + entityUidUrlSuffix + mtgRequestUid + "&" + previousMenu + "=" + timeMenu;
        return menuBuilder(new USSDMenu(promptMessage, nextUrl));

    }

    @RequestMapping(value = path + timeOnly)
    @ResponseBody
    public Request changeTimeOnlyMtgReq(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                        @RequestParam(value = entityUidParam, required = true) String mtgRequestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeOnly, mtgRequestUid, false));
        MeetingRequest meetingRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);
        String currentlySetTime = meetingRequest.getEventDateTimeAtSAST().format(getPreferredTimeFormat());

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "time." + promptKey, currentlySetTime, user));
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + timeOnly);

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + dateOnly)
    @ResponseBody
    public Request changeDateOnlyMtgReq(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                        @RequestParam(value = entityUidParam, required = true) String mtgRequestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(dateOnly, mtgRequestUid, false));
        MeetingRequest meetingRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);

        String currentlySetDate = meetingRequest.getEventDateTimeAtSAST().format(getPreferredDateFormat());

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "date." + promptKey, currentlySetDate, user));
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + dateOnly);

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + confirmMenu)
    @ResponseBody
    public Request confirmDetails(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                  @RequestParam(value = entityUidParam, required = true) String mtgRequestUid,
                                  @RequestParam(value = previousMenu, required = false) String priorMenu,
                                  @RequestParam(value = userInputParam, required = false) String userInput,
                                  @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        // todo: handle error if date parser finds nothing (e.g., user made a mistake and entered "J" or similar)

        User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(confirmMenu, mtgRequestUid, false));
        boolean isTimePast = false;

        if (!interrupted) {
            try {
                eventUtil.updateEventRequest(user.getUid(), mtgRequestUid, priorMenu, userInput);
            } catch (SeloParseDateTimeFailure e) {
                cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveMeetingMenu(priorMenu, mtgRequestUid, false));
                return handleDateTimeParseFailure(user, priorMenu, mtgRequestUid);
            }
        }

        MeetingRequest meeting = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);
        String dateString = convertToUserTimeZone(meeting.getEventStartDateTime(), getSAST()).format(dateTimeFormat);
        String[] confirmFields = new String[]{dateString, meeting.getName(), meeting.getEventLocation() };

        final boolean isInFuture = meeting.getEventStartDateTime().isAfter(Instant.now());

        String confirmPrompt = isInFuture ?
                getMessage(thisSection, confirmMenu, promptKey, confirmFields, user) :
                getMessage(thisSection, confirmMenu, promptKey + ".err.past", dateString, user);

        // based on user feedback, give options to return to any prior screen, then back here.

        USSDMenu thisMenu = new USSDMenu(confirmPrompt);

        if (isInFuture) {
            thisMenu.addMenuOption(meetingMenus + send + entityUidUrlSuffix + mtgRequestUid,
                    getMessage(thisSection + "." + confirmMenu + "." + optionsKey + "yes", user));
        }

        thisMenu.addMenuOption(composeBackUri(mtgRequestUid, timeOnly), composeBackMessage(user, "time"));
        thisMenu.addMenuOption(composeBackUri(mtgRequestUid, dateOnly), composeBackMessage(user, "date"));

        if (isInFuture) {
            thisMenu.addMenuOption(composeBackUri(mtgRequestUid, placeMenu), composeBackMessage(user, placeMenu));
            thisMenu.addMenuOption(composeBackUri(mtgRequestUid, subjectMenu) + "&groupId=" + meeting.getParent().getId(),
                    composeBackMessage(user, subjectMenu));
        }

        return menuBuilder(thisMenu);
    }

    private Request handleDateTimeParseFailure(User user, String priorMenu, String mtgRequestUid) throws URISyntaxException {
        final String keyRoot = mtgKey + ".parse.error." + priorMenu;
        final String prompt = getMessage(keyRoot, user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + priorMenu);
        return menuBuilder(menu);
    }

    @RequestMapping(value = path + send)
    @ResponseBody
    public Request sendMessage(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = entityUidParam) String mtgRequestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        try {
            eventRequestBroker.finish(user.getUid(), mtgRequestUid, true);
            return menuBuilder(new USSDMenu(getMessage(thisSection, send, promptKey, user), optionsHomeExit(user)));
        } catch (EventStartTimeNotInFutureException e) {
            return handleDateTimeNotInFuture(user, mtgRequestUid);
        }
    }

    private Request handleDateTimeNotInFuture(User user, String mtgRequestUid) throws URISyntaxException {
        USSDMenu menu = new USSDMenu(getMessage(thisSection, send, promptKey + ".err.past", user));
        menu.setFreeText(true);
        menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + timeMenu);
        return menuBuilder(menu);
    }

    /*
    SECTION: Meeting management menus follow after this
   The event management menu -- can't have too much complexity, just giving an RSVP total, and allowing cancel
   and change the date & time or location (other changes have too high a clunky UI vs number of use case trade off)
    */
    @RequestMapping(value = path + manageMeetingMenu)
    @ResponseBody
    public Request meetingManage(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber);
        Event event = eventBroker.load(eventUid);

        // todo: have a fallback menu in case user is also not part of the meeting
        if (user.equals(event.getCreatedByUser())) {
            menu = meetingCallerMenu(user, eventUid);
        } else {
            menu = meetingAttendeeMenu(user, event);
        }

        return menuBuilder(menu);
    }

    private USSDMenu meetingCallerMenu(User user, String eventUid) {

        final String eventSuffix = entityUidUrlSuffix + eventUid;
        USSDMenu menu = new USSDMenu(getMessage(thisSection, manageMeetingMenu, promptKey, user));

        menu.addMenuOption(meetingMenus + viewMeetingDetails + eventSuffix, getMessage(thisSection, viewMeetingDetails, "option", user));
        menu.addMenuOption(meetingMenus + newTime + eventSuffix, getMessage(thisSection, "change_" + timeOnly, "option", user));
        menu.addMenuOption(meetingMenus + newDate + eventSuffix, getMessage(thisSection, "change_" + dateOnly, "option", user));
        menu.addMenuOption(meetingMenus + changeMeetingLocation + eventSuffix, getMessage(thisSection, changeMeetingLocation, "option", user));
        menu.addMenuOption(meetingMenus + cancelMeeting + eventSuffix, getMessage(thisSection, cancelMeeting, "option", user));

        return menu;
    }

    @RequestMapping(value = path + viewMeetingDetails)
    @ResponseBody
    public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        Meeting meeting = eventBroker.loadMeeting(eventUid);

        String mtgDescription;

        if (meeting.isRsvpRequired()) {
            // todo: use enums instead of literal strings for the map (i.e., switch to RSVP toals DTO
            Map<String, Integer> rsvpResponses = eventManager.getMeetingRsvpTotals(meeting);
            int answeredYes = rsvpResponses.get("yes");
            int answeredNo = rsvpResponses.get("no");
            int noAnswer = rsvpResponses.get("no_answer");
            Group group = (Group) meeting.getParent(); // todo: !?!?!?
            String[] messageFields = new String[]{
                    group.getName(""),
                    meeting.getEventLocation(),
                    convertToUserTimeZone(meeting.getEventStartDateTime(), getSAST()).format(dateTimeFormat),
                    "" + eventManager.getNumberInvitees(meeting),
                    "" + answeredYes,
                    "" + answeredNo,
                    "" + noAnswer};
            mtgDescription = getMessage(thisSection, viewMeetingDetails, promptKey + ".rsvp", messageFields, sessionUser);
        } else {
            Group group = (Group) meeting.getParent(); // todo: !?!?!?
            String[] messageFields = new String[]{
                    group.getName(""),
                    meeting.getEventLocation(),
                    convertToUserTimeZone(meeting.getEventStartDateTime(), getSAST()).format(dateTimeFormat),
                    "" + eventManager.getNumberInvitees(meeting)};
            mtgDescription = getMessage(thisSection, viewMeetingDetails, promptKey, messageFields, sessionUser);
        }

        USSDMenu promptMenu = new USSDMenu(mtgDescription);
        promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + entityUidUrlSuffix + eventUid,
                getMessage(thisSection, viewMeetingDetails, optionsKey + "back", sessionUser)); // go back to 'manage meeting'
        promptMenu.addMenuOption(startMenu, getMessage(startMenu, sessionUser)); // go to GR home menu
        promptMenu.addMenuOption("exit", getMessage("exit.option", sessionUser)); // exit system
        return menuBuilder(promptMenu);
    }

    /*
    todo: add logging, etc.
     */

    @RequestMapping(value = path + changeMeetingLocation)
    public Request changeLocation(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String eventUid,
                                  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        MeetingRequest changeRequest = (requestUid == null) ? eventRequestBroker.createChangeRequest(user.getUid(), eventUid) :
                (MeetingRequest) eventRequestBroker.load(requestUid);
        cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(changeMeetingLocation, eventUid, changeRequest.getUid(), null));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeMeetingLocation, promptKey, changeRequest.getEventLocation(), user));
        menu.setFreeText(true);
        menu.setNextURI(editingMtgMenuUrl(modifyConfirm, eventUid, changeRequest.getUid(), changeMeetingLocation));
        return menuBuilder(menu);
    }

    @RequestMapping(path + newTime)
    public Request newMeetingTime(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String eventUid,
                                  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        MeetingRequest changeRequest = (requestUid == null) ? eventRequestBroker.createChangeRequest(user.getUid(), eventUid) :
                (MeetingRequest) eventRequestBroker.load(requestUid);
        cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(newTime, eventUid, changeRequest.getUid(), null));
        String existingTime = changeRequest.getEventDateTimeAtSAST().format(getPreferredTimeFormat());
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "time." + promptKey, existingTime, user));
        menu.setFreeText(true);
        menu.setNextURI(editingMtgMenuUrl(modifyConfirm, eventUid, changeRequest.getUid(), newTime));
        return menuBuilder(menu);
    }

    @RequestMapping(path + newDate)
    public Request newMeetingDate(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String eventUid,
                                  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        MeetingRequest changeRequest = (requestUid == null) ? eventRequestBroker.createChangeRequest(user.getUid(), eventUid) :
                (MeetingRequest) eventRequestBroker.load(requestUid);
        cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(newDate, eventUid, changeRequest.getUid(), null));
        String existingDate = changeRequest.getEventDateTimeAtSAST().format(getPreferredDateFormat());
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "change", "date." + promptKey, existingDate, user));
        menu.setFreeText(true);
        menu.setNextURI(editingMtgMenuUrl(modifyConfirm, eventUid, changeRequest.getUid(), newDate));
        return menuBuilder(menu);
    }

    // note: changing date and time do not come through here, but through a separate menu
    @RequestMapping(value = path + modifyConfirm)
    public Request modifyMeeting(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = entityUidParam) String eventUid,
                                 @RequestParam(value = requestUidParam) String requestUid,
                                 @RequestParam("action") String action,
                                 @RequestParam(userInputParam) String userInput,
                                 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

        userInput = (priorInput == null) ? userInput : priorInput;

        User user = userManager.findByInputNumber(inputNumber);
        cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(modifyConfirm, eventUid, requestUid, action)
                + "&prior_input=" + USSDUrlUtil.encodeParameter(userInput));

        USSDMenu menu;
        final String backUrl = meetingMenus + action + entityUidUrlSuffix + eventUid + "&" + requestUidParam + "=" + requestUid;

        eventUtil.updateExistingEvent(user.getUid(), requestUid, action, userInput);
        MeetingRequest changeMeetingRequest = (MeetingRequest) eventRequestBroker.load(requestUid);

        boolean isInFuture = changeMeetingRequest.getEventStartDateTime().isAfter(Instant.now());
        final String dateString = changeMeetingRequest.getEventDateTimeAtSAST().format(dateTimeFormat);

        final String[] updatedFields = new String[]{changeMeetingRequest.getEventLocation(), dateString};
        final String menuPrompt = isInFuture ?
                getMessage(thisSection, modifyConfirm, "confirm." + promptKey, updatedFields, user) :
                getMessage(thisSection, modifyConfirm, "confirm." + promptKey + ".err.past", dateString, user);

        menu = new USSDMenu(menuPrompt);

        if (isInFuture) {
            menu.addMenuOption(editingMtgMenuUrl(modifyConfirm + doSuffix, eventUid, requestUid, null),
                    getMessage(optionsKey + "yes", user));
        }

        menu.addMenuOption(backUrl, getMessage(thisSection, modifyConfirm, optionsKey + "back", user));

        List<String> modifyOptions = new ArrayList<>(Arrays.asList(newTime, newDate, changeMeetingLocation));

        modifyOptions.remove(action);
        if (!isInFuture) {
            modifyOptions.remove(changeMeetingLocation);
        }

        for (String otherMenu : modifyOptions)
            menu.addMenuOption(editingMtgMenuUrl(otherMenu, eventUid, requestUid, null), getMessage(thisSection, modifyConfirm, optionsKey + otherMenu, user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + modifyConfirm + doSuffix)
    public Request modifyMeetingSend(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = entityUidParam) String eventUid,
                                     @RequestParam(value = requestUidParam) String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        try {
            String menuPrompt = getMessage(thisSection, modifyConfirm, promptKey + ".done", user);
            eventRequestBroker.finishEdit(user.getUid(), eventUid, requestUid);
            return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(user)));
        } catch (EventStartTimeNotInFutureException e) {
            // given structure of UI above, this shouldn't happen, but just in case ...
            USSDMenu menu = new USSDMenu(getMessage(thisSection, modifyConfirm, promptKey + ".err.past", user));
            menu.addMenuOption(editingMtgMenuUrl(newTime, eventUid, requestUid, null), getMessage(thisSection, modifyConfirm, optionsKey + newTime, user));
            menu.addMenuOption(editingMtgMenuUrl(newDate, eventUid, requestUid, null), getMessage(thisSection, modifyConfirm, optionsKey + newDate, user));
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = path + cancelMeeting)
    public Request meetingCancel(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber); // not saving URL on this one
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, cancelMeeting, promptKey, sessionUser));
        promptMenu.addMenuOption(meetingMenus + cancelMeeting + doSuffix + entityUidUrlSuffix + eventUid,
                                 getMessage(optionsKey + "yes", sessionUser));
        promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + entityUidUrlSuffix + eventUid,
                                 getMessage(optionsKey + "no", sessionUser));

        return menuBuilder(promptMenu);
    }

    @RequestMapping(value = path + cancelMeeting + doSuffix)
    public Request meetingCancelConfirmed(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        String menuPrompt = getMessage(thisSection, modifyConfirm, "cancel.done", sessionUser);
        eventBroker.cancel(sessionUser.getUid(), eventUid);
        return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(sessionUser)));
    }

    /*
    Method and menus for a non-creating user to view & change their attendance
     */
    private USSDMenu meetingAttendeeMenu(User user, Event event) {

        final EventLog userResponse = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);

        final String attendeeKey = "attendee";
        final String suffix = entityUidUrlSuffix + event.getUid();
        final String basePath = meetingMenus + "change-response" + suffix + "&response=";
        final String[] fields = new String[] { event.getAncestorGroup().getName(""),
                event.getEventDateTimeAtSAST().format(dateTimeFormat)};

        USSDMenu menu;
        if (userResponse != null) {
            if (EventRSVPResponse.YES.equals(userResponse.getResponse())) {
                menu = new USSDMenu(getMessage(thisSection, attendeeKey, "rsvpyes." + promptKey, fields, user));
                menu.addMenuOption(basePath + "no", getMessage(thisSection, attendeeKey, optionsKey + "rsvpno", user));
            } else {
                menu = new USSDMenu(getMessage(thisSection, attendeeKey, "rsvpno." + promptKey, fields, user));
                menu.addMenuOption(basePath + "yes", getMessage(thisSection, attendeeKey, optionsKey + "rsvpyes", user));
            }
        } else {
            menu = new USSDMenu(getMessage(thisSection, attendeeKey, "noreply." + promptKey, fields, user));
            menu.addMenuOption(basePath + "yes", getMessage(thisSection, "attendee", optionsKey + "rsvpyes", user));
            menu.addMenuOption(basePath + "no", getMessage(thisSection, "attendee", optionsKey + "rsvpno", user));
        }

        menu.addMenuOption(meetingMenus + viewMeetingDetails + suffix, getMessage(thisSection, viewMeetingDetails, "option", user));

        return menu;
    }

    @RequestMapping(value = path + "change-response")
    @ResponseBody
    public Request changeAttendeeResponseDo(@RequestParam(value = phoneNumber) String inputNumber,
                                            @RequestParam(value = entityUidParam) String eventUid,
                                            @RequestParam(value = "response") String response) throws URISyntaxException {

        // todo: should probably just make sure that this user is in fact part of the meeting (also might not want to default to "no")

        final User user = userManager.findByInputNumber(inputNumber);
        final Event event = eventBroker.load(eventUid);
        final EventRSVPResponse userResponse = "yes".equalsIgnoreCase(response) ? EventRSVPResponse.YES : EventRSVPResponse.NO;

        eventLogBroker.rsvpForEvent(event.getUid(), user.getUid(), userResponse);

        final String key = "att_changed";
        final String suffix = entityUidUrlSuffix + eventUid;

        USSDMenu menu = new USSDMenu(getMessage(thisSection, key, promptKey, user));
        menu.addMenuOption(meetingMenus + manageMeetingMenu + suffix, getMessage(thisSection, key, optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user));

        return menuBuilder(menu);
    }

    /*
    A couple of helper methods that are quite specific to flow & structure of this controller
    todo: consider abstracting & moving to USSDUrlUtils
     */

    private String composeBackUri(String entityUid, String backMenu) {
        return meetingMenus + backMenu + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + backMenu + "&revising=1"
                + "&next_menu=" + confirmMenu;
    }

    /* Another helper function to compose next URL */
    public String nextUrl(String currentMenu, String entityUid) {
        return meetingMenus + nextMenu(currentMenu) + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + currentMenu;
    }

    /* Helper method to compose URL for going to confirmation screen after revisions */
    private String confirmUrl(String currentMenu, String entityUid) {
        return meetingMenus + confirmMenu + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + currentMenu +
                "&revising=1";
    }

    private String composeBackMessage(User user, String backMenu) {
        return getMessage(thisSection + "." + confirmMenu + "." + optionsKey + backMenu, user);
    }

}
