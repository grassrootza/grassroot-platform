package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

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
    private USSDMeetingController ussdMeetingController;

    @InjectMocks
    private USSDEventUtil ussdEventUtil;

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
//        Event dummyEvent = new Event("", testUser);
        Event dummyEvent = null; // todo: new design?
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
        for (Group group : existingGroupList) {
            group.addMember(testUser);
        }
        Page<Group> groupPage = new PageImpl<Group>(existingGroupList);

        List<Event> emptyMeetingList = new ArrayList<>();
//        Event dummyEvent = new Event("", testUser);
        Event dummyEvent = null; // todo: new design?
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
        Group group = new Group("someGroup", testUser);
        Timestamp startTime = Timestamp.from(Instant.now());


        List<Event> upcomingMeetingList = Arrays.asList(
                new Meeting("meeting1", startTime, testUser, group, "someLocation"),
                new Meeting("meeting2", startTime, testUser, group, "someLocation"),
                new Meeting("meeting3", startTime, testUser, group, "someLocation"));

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
        Set<MembershipInfo> members = ordinaryMember("0801112345");
        members.addAll(organizer(testUser));
        String secondUrlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupId=" + testGroup.getId(), "0801112345");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, firstUrlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, secondUrlToSave)).thenReturn(testUser);
        log.info("ZOGG : About to call broker create with members ..." + members);
        when(groupBrokerMock.create(testUser.getUid(), "", null, members, GroupPermissionTemplate.DEFAULT_GROUP, null)).
                thenReturn(testGroup);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("request", "0801112345")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + secondUrlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, firstUrlToSave);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, secondUrlToSave);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, secondUrlToSave);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(groupBrokerMock, times(1)).create(testUser.getUid(), "", null, members, GroupPermissionTemplate.DEFAULT_GROUP, null);
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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        // note: deliberately pass a badly formed number and then check that only the well-formed one is passed to services
        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId()).
                param("request", numbersToInput)).andExpect(status().isOk());
        // note: this call simulates interruption -- it may add number a second time (hence 2 calls below), but better safe
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(groupBrokerMock, times(2)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801112345"));
        verifyZeroInteractions(eventManagementServiceMock);

    }

   /* @Test
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
        when(eventManagementServiceMock.createMeeting(testUser, 1L)).thenReturn(testMeeting);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId()).
                param("request", "0")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave2).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, urlToSave2);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave2);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).createMeeting(testUser, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }*/

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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("prior_input", "0801112345 080111234")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801112345"));
        // verify(groupManagementServiceMock, times(1)).addNumbersToGroup(testGroup.getId(), Arrays.asList("0801112345"), testUser, true);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    @Test
    public void settingSubjectShouldWork() throws Exception {

        // todo: make sure to cover the full range of cases, e.g., revising then interrupted then returned
        User testUser = new User(testUserPhone);
        Group testGroup = new Group("gc1", testUser);
        Event testMeeting = new Meeting("unit test", Timestamp.from(Instant.now()), testUser, testGroup, "someLocation");

        testGroup.setId(1L);
        testMeeting.setAppliesToGroup(testGroup);
        testMeeting.setId(1L);

        String urlToCheck = saveMeetingMenu("subject", 1L, false);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToCheck)).thenReturn(testUser);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("prior_menu", "group")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToCheck).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToCheck);
        verifyNoMoreInteractions(userManagementServiceMock);
        // verify(eventManagementServiceMock, times(1)).createMeeting(testUserPhone, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void settingLocationShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String testEventUid = MeetingRequest.makeEmpty().getUid();
        String urlToSave = saveMeetingMenu("place", testEventUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "place").param(phoneParam, testUserPhone).param("entityUid", testEventUid).
                param("prior_menu", "subject").param("request", "unit test")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).updateName(testUser.getUid(), testEventUid, "unit test");
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void settingDateTimeShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        String requestUid = MeetingRequest.makeEmpty().getUid();
        String urlToSave = saveMeetingMenu("time", requestUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "time").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("prior_menu", "place").param("request", "JoziHub")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("entityUid", requestUid)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).updateMeetingLocation(testUser.getUid(), requestUid, "JoziHub");
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    // major todo: test revise and return

    @Test
    public void confirmationMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("gc1", testUser);
        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
        String requestUid = meetingForTest.getUid();
        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        meetingForTest.setEventStartDateTime(Timestamp.valueOf(forTimestamp));
        String urlToSave = saveMeetingMenu("confirm", requestUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(meetingForTest);

        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                param("prior_menu", "time").param("request", "Tomorrow 7am")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).updateStartTimestamp(testUser.getUid(), requestUid, Timestamp.valueOf(forTimestamp));
        verifyNoMoreInteractions(eventRequestBrokerMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void meetingChangeTimeShouldWork() throws Exception {
        User testUser = new User(testUserPhone);
        String urlToSave = saveMeetingMenu("time_only", 1L, false) + "&next_menu=confirm";
        Event meetingForTest = new Meeting("unit test", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("someGroup", testUser), "someLoca");
        meetingForTest.setId(1L);

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
        String urlToSave = saveMeetingMenu("date_only", 1L, false) + "&next_menu=confirm";
        Event meetingForTest = new Meeting("unit test", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("someGroup", testUser), "someLoc");
        meetingForTest.setId(1L);

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
        Group testGroup = new Group("tg1", testUser);
        Timestamp timestamp = Timestamp.valueOf(DateTimeUtil.parseDateTime("Tomorrow 7am"));
        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
        meetingForTest.setEventStartDateTime(timestamp);
        String requestUid = meetingForTest.getUid();

        String urlToSave = saveMeetingMenu("confirm", requestUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(meetingForTest);

        List<String> nineAmVariations = Arrays.asList("09:00", "09 00", "900", "9:00 am", "9am");
        List<String> onePmVariations = Arrays.asList("13:00", "13 00", "1300", "1:00 pm", "1pm");

        for (String time : nineAmVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                    param("prior_menu", "time_only").param("revising", "1").param("request", time)).andExpect(status().isOk());
        }

        for (String time : onePmVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                    param("prior_menu", "time_only").param("revising", "1").param("request", time)).andExpect(status().isOk());
        }

        // not doing the full range of checks as those are tested above, here just verifying no extraneous calls
        verify(eventRequestBrokerMock, times(nineAmVariations.size())).updateStartTimestamp(testUser.getUid(), requestUid, timestamp);
        verify(eventRequestBrokerMock, times(onePmVariations.size())).updateStartTimestamp(testUser.getUid(), requestUid, timestamp);
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("tg1", testUser);
        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        MeetingRequest testMeeting = MeetingRequest.makeEmpty(testUser, testGroup);
        testMeeting.setEventStartDateTime(Timestamp.valueOf(forTimestamp));
        String requestUid = testMeeting.getUid();

        String urlToSave = saveMeetingMenu("confirm", requestUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        // as above, specifying string makes sure it gets formatted appropriately (keep an eye on year though)
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(testMeeting);

        // todo : test for just YY, once done
        List<String> bloomVariations = Arrays.asList("16-06", "16 06", "16/06", "16-6", "16 6", "16/6",
                                                     "16-06-2016", "16 06 2016", "16/06/2016", "16-6-2016", "16/6/2016");

        for (String date : bloomVariations) {
            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "1").
                    param("prior_menu", "date_only").param("revising", "1").param("request", date)).andExpect(status().isOk());
        }

        verify(eventRequestBrokerMock, times(bloomVariations.size())).updateStartTimestamp(testUser.getUid(), requestUid,
                                                                                           Timestamp.valueOf(forTimestamp));
    }

    @Test
    public void sendConfirmationScreenShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        MeetingRequest meetingRequest = MeetingRequest.makeEmpty();
        String requestUid = meetingRequest.getUid();

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        LocalDateTime forTimestamp = DateTimeUtil.parseDateTime("Tomorrow 7am");
        String confirmedTime = forTimestamp.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"));

        mockMvc.perform(get(path + "send").param(phoneParam, testUserPhone).param("entityUid", requestUid))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).finish(testUser.getUid(), requestUid, true);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void manageMeetingMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group somegroup = new Group("somegroup", testUser);
        Event testMeeting = new Meeting("someMeeting", Timestamp.from(Instant.now()), testUser, somegroup, "someLoc");
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
        Group somegroup = new Group("somegroup", testUser);
        Event testMeeting = new Meeting("someMeeting", Timestamp.from(Instant.now()), testUser, somegroup, "someLoc");
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
        when(eventManagementServiceMock.getMeetingRsvpTotals(testMeeting)).thenReturn(meetingResults);
        when(eventManagementServiceMock.getNumberInvitees(testMeeting)).thenReturn(546 + 115 + 54);

        mockMvc.perform(get(path + "details").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).loadEvent(anyLong());
        verify(eventManagementServiceMock, times(1)).getMeetingRsvpTotals(any(Event.class));
        verify(eventManagementServiceMock, times(1)).getMeetingRsvpTotals(any(Event.class));
        verify(eventManagementServiceMock, times(1)).getNumberInvitees(any(Event.class));
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void changeDateOnlyShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "someloc");
        testMeeting.setId(1L);
        String urlToSave = saveMeetingMenu("date_only", 1L, false) + "&next_menu=changeDateTime";

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
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "someloc");
        testMeeting.setId(1L);
        String urlToSave = saveMeetingMenu("time_only", 1L, false) + "&next_menu=changeDateTime";

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
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.of(2016, 06, 15, 10, 0)), testUser, new Group("somegroup", testUser), "someloc");
        testMeeting.setId(1L);

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
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void modifyDateTimeSendShouldWork() throws Exception {

//        testMeeting.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now()));
        User testUser = new User(testUserPhone);
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "someloc");
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "changeDateTime").param("value", "16-06-2016 09:00")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        // todo. new design?
//        verify(eventManagementServiceMock, times(1)).setEventTimestampToStoredString(testMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void changeLocationPromptShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "JoziHub");
        testMeeting.setId(1L);
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
        Event testMeeting = new Meeting("test meeting", Timestamp.from(Instant.now()), testUser, new Group("somegroup", testUser), "someloc");
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
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "someloc");
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
        Event testMeeting = new Meeting("test meeeting", Timestamp.valueOf(LocalDateTime.now()), testUser, new Group("somegroup", testUser), "JoziHub");
        testMeeting.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "changeLocation").param("value", "Braam")).andExpect(status().isOk());

        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("eventId", "" + testMeeting.getId()).
                param("action", "cancel").param("value", "Braam")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString(), eq(null));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventBrokerMock, times(1)).cancel(testUser.getUid(), testMeeting.getUid());
        // verify(eventManagementServiceMock, times(1)).setLocation(testMeeting.getId(), "Braam");
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

}
