package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveMeetingMenu;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveMenuUrlWithInput;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveMtgMenuWithAction;

/**
 * Created by luke on 2015/11/26.
 */
public class USSDMeetingControllerTest extends USSDAbstractUnitTest {

    // todo: log stuff
    private static final Logger log = LoggerFactory.getLogger(USSDMeetingControllerTest.class);

    private static final String testUserPhone = "27601110000";
    private static final String phoneParam = "msisdn";

    private static final String path = "/ussd/mtg/";
    private static final USSDSection thisSection = USSDSection.MEETINGS;

    @InjectMocks
    USSDMeetingController ussdMeetingController;

    @InjectMocks
    USSDEventUtil ussdEventUtil;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdMeetingController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();

        wireUpMessageSourceAndGroupUtil(ussdMeetingController, ussdGroupUtil);
        ussdEventUtil.setMessageSource(messageSource());
        ussdMeetingController.setEventUtil(ussdEventUtil);
    }

    @Test
    public void meetingStartMenuNoUpcomingMeetingsAndNoGroups() throws Exception {

        User testUser = new User(testUserPhone);
        List<Event> emptyMeetingList = new ArrayList<>();
        List<Group> emptyGroupList = new ArrayList<>();
        Event dummyEvent = new Event("", testUser);
        testUser.setGroupsPartOf(emptyGroupList);
        dummyEvent.setId(0L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.getUpcomingEventsUserCreated(testUser)).thenReturn(emptyMeetingList);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).getUpcomingEventsUserCreated(any(User.class));
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void meetingStartMenuNoUpcomingMeetingsAndExistingGroups() throws Exception {

        User testUser = new User(testUserPhone);
        List<Group> existingGroupList = Arrays.asList(new Group("gc1", testUser),
                                                      new Group("gc2", testUser),
                                                      new Group("gc3", testUser));
        testUser.setGroupsPartOf(existingGroupList);
        Page<Group> groupPage = new PageImpl<Group>(existingGroupList);

        List<Event> emptyMeetingList = new ArrayList<>();
        Event dummyEvent = new Event("", testUser);
        dummyEvent.setId(0L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.getUpcomingEventsUserCreated(testUser)).thenReturn(emptyMeetingList);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(groupPage);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(any(User.class), anyInt(), anyInt());
        verify(eventManagementServiceMock, times(1)).getUpcomingEventsUserCreated(testUser);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void meetingStartWithUpcomingMeetings() throws Exception {

        User testUser = new User(testUserPhone);
        List<Event> upcomingMeetingList = Arrays.asList(new Event("meeting1", testUser),
                                                        new Event("meeting2", testUser),
                                                        new Event("meeting3", testUser));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.getUpcomingEventsUserCreated(testUser)).thenReturn(upcomingMeetingList);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(eventManagementServiceMock, times(1)).getUpcomingEventsUserCreated(testUser);
    }

    @Test
    public void newGroupPromptShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String urlToSave = USSDSection.MEETINGS.toPath() + "newgroup";

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "newgroup").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    /*
    Series of tests, for a single method, the one in the guts of the meeting menu, which handles the creation of a group
    and addition of numbers to it.

    */

    @Test
    public void addingNumbersToNewGroupMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);
        String firstUrlToSave = saveMenuUrlWithInput(thisSection, "group", "", "0801112345");
        String secondUrlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupId=" + testGroup.getId(), "0801112345");

        log.info("Running the test, where the url to save is ... " + firstUrlToSave);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, firstUrlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, secondUrlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.createNewGroup(eq(testUser), anyListOf(String.class))).thenReturn(testGroup);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("request", "0801112345")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + secondUrlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, firstUrlToSave);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, secondUrlToSave);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, secondUrlToSave);
        verify(groupManagementServiceMock, times(1)).createNewGroup(testUser, Arrays.asList("0801112345"));
        verify(groupManagementServiceMock, times(1)).addNumbersToGroup(testGroup.getId(), Arrays.asList("0801112345"));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void addingNumbersToExistingGroupMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);
        String numbersToInput = "0801112345 080111234";
        String urlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupId=" + testGroup.getId(), numbersToInput);
        log.info("Testing adding numbers to existing group, with this Url to save ..." + urlToSave);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        // note: deliberately pass a badly formed number and then check that only the well-formed one is passed to services
        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId()).
                param("request", numbersToInput)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).addNumbersToGroup(testGroup.getId(), Arrays.asList("0801112345"));
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    @Test
    public void enteringZeroToStopGroupCreationShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);
        Event testMeeting = new Event("unit test", testUser, testGroup);
        testMeeting.setId(1L);
        String urlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupId=" + testGroup.getId(), "0");
        String urlToSave2 = saveMenuUrlWithInput(thisSection, "group",
                                                 "?groupId=" + testGroup.getId() + "&eventId=" + testMeeting.getId(), "0");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave2)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUserPhone, 1L)).thenReturn(testMeeting);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId()).
                param("request", "0")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave2).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, urlToSave2);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave2);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).createMeeting(testUserPhone, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void enteringNoValidNumbersShouldGiveError() throws Exception {

        User testUser = new User(testUserPhone);
        String urlToSave = saveMenuUrlWithInput(thisSection, "group", "", "0");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("request", "0")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    @Test
    public void returningToComplexGroupMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("gc1", testUser);
        testGroup.setId(1L);

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("prior_input", "0801112345 080111234")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).addNumbersToGroup(testGroup.getId(), Arrays.asList("0801112345"));
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    @Test
    public void settingSubjectShouldWork() throws Exception {

        // todo: make sure to cover the full range of cases, e.g., revising then interrupted then returned
        User testUser = new User(testUserPhone);
        Group testGroup = new Group("gc1", testUser);
        Event testMeeting = new Event("unit test", testUser);

        testGroup.setId(1L);
        testMeeting.setAppliesToGroup(testGroup);
        testMeeting.setId(1L);

        String urlToCheck = saveMeetingMenu("subject", 1L, false);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToCheck)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUserPhone, testGroup.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("prior_menu", "group")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToCheck).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToCheck);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).createMeeting(testUserPhone, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void settingLocationShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("place", 1L, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "place").param(phoneParam, testUserPhone).param("eventId", "1").
                param("prior_menu", "subject").param("request", "unit test")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setSubject(1L, "unit test");
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void settingDateTimeShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("time", 1L, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "time").param(phoneParam, testUserPhone).param("eventId", "1").
                param("prior_menu", "place").param("request", "JoziHub")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setLocation(1L, "JoziHub");
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    // major todo: test revise and return

    @Test
    public void confirmationMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("confirm", 1L, false);

        Event meetingForTest = new Event("unit test", testUser);
        meetingForTest.setEventLocation("JoziHub");
        Group testGroup = new Group("gc1", testUser);
        testGroup.setId(1L);
        meetingForTest.setAppliesToGroup(testGroup);
        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        meetingForTest.setEventStartDateTime(Timestamp.valueOf(forTimestamp));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.setSendBlock(1L)).thenReturn(meetingForTest);
        when(eventManagementServiceMock.setEventTimestamp(1L, Timestamp.valueOf(forTimestamp))).thenReturn(meetingForTest);

        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                param("prior_menu", "time").param("request", "Tomorrow 7am")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setSendBlock(1L);
        verify(eventManagementServiceMock, times(1)).setEventTimestamp(1L, Timestamp.valueOf(forTimestamp));
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void meetingChangeTimeShouldWork() throws Exception {
        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("time_only", 1L, false) + "?next_menu=confirm";
        Event meetingForTest = new Event("unit test", testUser);
        meetingForTest.setId(1L);
        meetingForTest.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(1L)).thenReturn(meetingForTest);

        mockMvc.perform(get(path + "time_only").param(phoneParam, testUserPhone).param("eventId", "1").
                param("next_menu", "confirm")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(1L);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void meetingChangeDateShouldWork() throws Exception {
        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("date_only", 1L, false) + "?next_menu=confirm";
        Event meetingForTest = new Event("unit test", testUser);
        meetingForTest.setId(1L);
        meetingForTest.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(1L)).thenReturn(meetingForTest);

        mockMvc.perform(get(path + "date_only").param(phoneParam, testUserPhone).param("eventId", "1").
                param("next_menu", "confirm")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(1L);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void timeProcessingShouldWork() throws Exception {
        User testUser = new User(testUserPhone);

        Event meetingForTest = new Event("unit test", testUser);
        meetingForTest.setId(1L);
        Group testGroup = new Group("tg1", testUser);
        testGroup.setId(1L);
        meetingForTest.setAppliesToGroup(testGroup);
        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        meetingForTest.setEventStartDateTime(Timestamp.valueOf(forTimestamp));

        String urlToSave = saveMeetingMenu("confirm", 1L, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.setSendBlock(1L)).thenReturn(meetingForTest);
        // specifying string in stub means tests will fail if strings aren't reformatted properly
        when(eventManagementServiceMock.changeMeetingTime(1L, "09:00")).thenReturn(meetingForTest);
        when(eventManagementServiceMock.changeMeetingTime(1L, "13:00")).thenReturn(meetingForTest);

        List<String> nineAmVariations = Arrays.asList("09:00", "09 00", "900", "9:00 am", "9am");
        List<String> onePmVariations = Arrays.asList("13:00", "13 00", "1300", "1:00 pm", "1pm");

        for (String time : nineAmVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                    param("prior_menu", "time_only").param("revising", "1").param("request", time)).andExpect(status().isOk());
        }

        for (String time : onePmVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                    param("prior_menu", "time_only").param("revising", "1").param("request", time)).andExpect(status().isOk());
        }

        // not doing the full range of checks as those are tested above, here just verifying no extraneous calls
        verify(eventManagementServiceMock, times(nineAmVariations.size())).changeMeetingTime(1L, "09:00");
        verify(eventManagementServiceMock, times(onePmVariations.size())).changeMeetingTime(1L, "13:00");
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {
        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("unit test", testUser);
        Group testGroup = new Group("tg1", testUser);
        testMeeting.setId(1L);
        testGroup.setId(1L);
        testMeeting.setAppliesToGroup(testGroup);
        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        testMeeting.setEventStartDateTime(Timestamp.valueOf(forTimestamp));

        String urlToSave = saveMeetingMenu("confirm", 1L, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.setSendBlock(1L)).thenReturn(testMeeting);
        // as above, specifying string makes sure it gets formatted appropriately (keep an eye on year though)
        when(eventManagementServiceMock.changeMeetingDate(1L, "16-06-2015")).thenReturn(testMeeting);

        // todo : test for just YY, once done
        List<String> bloomVariations = Arrays.asList("16-06", "16 06", "16/06", "16-6", "16 6", "16/6",
                                                     "16-06-2015", "16 06 2015", "16/06/2015", "16-6-2015", "16/6/2015");

        for (String date : bloomVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                    param("prior_menu", "date_only").param("revising", "1").param("request", date)).andExpect(status().isOk());
        }

        verify(eventManagementServiceMock, times(bloomVariations.size())).changeMeetingDate(1L, "16-06-2015");

    }

    @Test
    public void sendConfirmationScreenShouldWork() throws Exception {

        User testUser = new User(testUserPhone);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        String confirmedTime = forTimestamp.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"));

        mockMvc.perform(get(path + "send").param(phoneParam, testUserPhone).param("eventId", "1").
                param("confirmed_time", confirmedTime)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).removeSendBlock(1L);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void manageMeetingMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event(testUser, EventType.Meeting);
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "manage").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(testMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void viewMeetingDetailsShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event(testUser, EventType.Meeting, true);
        testMeeting.setId(1L);

        Map<String, String> meetingDetails = new HashMap<>();
        meetingDetails.put("groupName", "Test Group");
        meetingDetails.put("location", "JoziHub");
        meetingDetails.put("dateTimeString", "Sat 23 Sep 2055, 11:11 am");

        Map<String, Integer> meetingResults = new HashMap<>();
        meetingResults.put("yes", 115);
        meetingResults.put("no", 54);
        meetingResults.put("no_answer", 546);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);
        when(eventManagementServiceMock.getEventDescription(testMeeting)).thenReturn(meetingDetails);
        when(eventManagementServiceMock.getMeetingRsvpTotals(testMeeting)).thenReturn(meetingResults);
        when(eventManagementServiceMock.getNumberInvitees(testMeeting)).thenReturn(546 + 115 + 54);

        mockMvc.perform(get(path + "details").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(anyLong());
        verify(eventManagementServiceMock, times(1)).getEventDescription(any(Event.class));
        verify(eventManagementServiceMock, times(1)).getMeetingRsvpTotals(any(Event.class));
        verify(eventManagementServiceMock, times(1)).getMeetingRsvpTotals(any(Event.class));
        verify(eventManagementServiceMock, times(1)).getNumberInvitees(any(Event.class));
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void changeDateOnlyShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeeting", testUser);
        testMeeting.setId(1L);
        testMeeting.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));
        String urlToSave = saveMeetingMenu("date_only", 1L, false) + "?next_menu=changeDateTime";

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "date_only").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("next_menu", "changeDateTime")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(testMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void changeTimeOnlyShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeeting", testUser);
        testMeeting.setId(1L);
        testMeeting.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));
        String urlToSave = saveMeetingMenu("time_only", 1L, false) + "?next_menu=changeDateTime";

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "time_only").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("next_menu", "changeDateTime")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(testMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void changeDateAndTimeShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);
        testMeeting.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.of(2015, 06, 15, 10, 0))); // switch year soon!

        String dateUrlToSave = saveMtgMenuWithAction("changeDateTime", testMeeting.getId(), "date_only");
        String timeUrlToSave = saveMtgMenuWithAction("changeDateTime", testMeeting.getId(), "time_only");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, dateUrlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, timeUrlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "changeDateTime").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "time_only").param("request", "09:00")).andExpect(status().isOk());

        mockMvc.perform(get(path + "changeDateTime").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "date_only").param("request", "16-06")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(2)).loadEvent(testMeeting.getId());
        verify(eventManagementServiceMock, times(1)).storeDateTimeString(testMeeting.getId(), "15-06-2015 09:00"); // year!
        verify(eventManagementServiceMock, times(1)).storeDateTimeString(testMeeting.getId(), "16-06-2015 10:00"); // year!
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void modifyDateTimeSendShouldWork() throws Exception {

//        testMeeting.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));
        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "changeDateTime").param("value", "16-06-2015 09:00")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setEventTimestampToStoredString(testMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void changeLocationPromptShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);
        testMeeting.setEventLocation("JoziHub");
        String urlToSave = "mtg/changeLocation?eventId=1";

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testMeeting.getId())).thenReturn(testMeeting);

        mockMvc.perform(get(path + "changeLocation").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(anyLong());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void cancelMeetingPromptShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        mockMvc.perform(get(path + "cancel").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        // note: not verifying interaction times with other services, until have permissions / filters in place

    }

    @Test
    public void meetingModificationConfirmationScreenShouldWork() throws Exception {

        User testUser = new User(testUserPhone);

        List<String[]> actionsAndInputs = Arrays.asList(new String[]{ "error", "wrong"},
                                                        new String[]{ "changeLocation", "Braam"},
                                                        new String[]{ "cancel", "1"});

        String urlToSave;
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);
        when(eventManagementServiceMock.loadEvent(1L)).thenReturn(testMeeting);

        for (String[] actions : actionsAndInputs) {
            urlToSave = "mtg/modify?eventId=" + testMeeting.getId() + "&action=" + actions[0] + "&prior_input=" + actions[1];
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            mockMvc.perform(get(path + "modify").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                    param("action", actions[0]).param("request", actions[1])).andExpect(status().isOk());
        }

        verify(userManagementServiceMock, times(actionsAndInputs.size())).findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void meetingModificationSendShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Event("test meeting", testUser);
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "changeLocation").param("value", "Braam")).andExpect(status().isOk());

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "cancel").param("value", "Braam")).andExpect(status().isOk());

        Timestamp modifiedTimestamp = Timestamp.valueOf(DateTimeUtil.parseDateTime("Sunday 9am"));

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), eq(null));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).cancelEvent(testMeeting.getId());
        verify(eventManagementServiceMock, times(1)).setLocation(testMeeting.getId(), "Braam");
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

}
