package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.util.DateTimeUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



/*
 * Paballo Ditshego 06/01/2016
 */
public class MeetingControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);
    private static final Instant oneDayAway = Instant.now().plus(1, ChronoUnit.DAYS);

    @InjectMocks
    private MeetingController meetingController;

    @Before
    public void setUp() {
        setUp(meetingController);
    }

     @Test
    public void shouldShowMeetingDetails() throws Exception {

        Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway, sessionTestUser, dummyGroup, "some place");

         logger.info("ZOG: dummyMeetingIs: {} ", dummyMeeting);

        ResponseTotalsDTO testCount = new ResponseTotalsDTO();
        testCount.setYes(1);
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);
        when(eventLogManagementServiceMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);

        mockMvc.perform(get("/meeting/view").param("eventUid", dummyMeeting.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("entityUid", is(dummyMeeting.getUid()))))
                .andExpect(model().attribute("responseTotals", hasProperty("yes", is(1))))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));

        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);
        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogManagementServiceMock, times(1)).getResponseCountForEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void testCreateMeetingWorks() throws Exception {

        Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway, sessionTestUser, dummyGroup, "some place");
        dummyMeeting.setRsvpRequired(true);
        dummyMeeting.setReminderType(EventReminderType.CUSTOM);
        dummyMeeting.setCustomReminderMinutes(60);

        mockMvc.perform(post("/meeting/create").sessionAttr("meeting", dummyMeeting)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("selectedGroupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(redirectedUrl("/group/view?groupUid=" + dummyGroup.getUid()));

        verify(eventBrokerMock, times(1)).createMeeting(sessionTestUser.getUid(), dummyGroup.getUid(), JpaEntityType.GROUP, "test meeting",
                                                        oneDayAway.atZone(DateTimeUtil.getSAST()).toLocalDateTime(),
                                                        "some place", false, true, false, EventReminderType.CUSTOM, 60,
                                                        "", Collections.emptySet());

        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void createMeetingIndexWorksWithGroupSpecified() throws Exception {

        Group testGroup = new Group("", sessionTestUser);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get("/meeting/create").param("groupUid", testGroup.getUid()))
                .andExpect((view().name("meeting/create"))).andExpect(status().isOk())
                .andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid()))))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void createMeetingIndexWorksWithGroupNotSpecified() throws Exception {

        Group dummyGroup = new Group("", sessionTestUser);
        Set<Group> dummyGroups = Collections.singleton(dummyGroup);

        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser,
                                                  Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(dummyGroups);

        mockMvc.perform(get("/meeting/create")).andExpect(status().isOk())
                .andExpect((view().name("meeting/create")))
                .andExpect(model().attribute("userGroups", hasItem(dummyGroup)));

        verify(userManagementServiceMock, times(1)).load(sessionTestUser.getUid());
        verify(permissionBrokerMock, times(1)).getActiveGroups(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
    }

    @Test
    public void meetingModificationWorks() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway,
                                           sessionTestUser, testGroup, "some place");

        List<User> listOfDummyYesResponses = Arrays.asList(new User("", "testUser"));
        ResponseTotalsDTO testCount = new ResponseTotalsDTO();
        testCount.setYes(1);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);
        when(eventLogManagementServiceMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(dummyMeeting)).thenReturn(listOfDummyYesResponses);

        mockMvc.perform(post("/meeting/modify").sessionAttr("meeting", dummyMeeting).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("meeting", hasProperty("entityUid", is(dummyMeeting.getUid()))))
                .andExpect(model().attribute("responseTotals", hasProperty("yes", is(1))))
                .andExpect(view().name("meeting/view"));

        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventBrokerMock, times(1)).updateMeeting(sessionTestUser.getUid(), dummyMeeting.getUid(), dummyMeeting.getName(),
                                                        dummyMeeting.getEventStartDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime(),
                                                        dummyMeeting.getEventLocation());
        verifyNoMoreInteractions(eventBrokerMock);
        verify(eventManagementServiceMock, times(1)).getRSVPResponses(dummyMeeting);
        verify(eventLogManagementServiceMock, times(1)).getResponseCountForEvent(dummyMeeting);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void rsvpNoShouldWork() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway,
                                           sessionTestUser, testGroup, "some place");

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);

        mockMvc.perform(post("/meeting/rsvp").param("eventUid", dummyMeeting.getUid()).param("answer", "no"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));

        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.NO);
        verifyZeroInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);
    }


    @Test
    public void rsvpYesShouldWork() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway,
                                           sessionTestUser, testGroup, "some place");

        ResponseTotalsDTO testCount = new ResponseTotalsDTO();
        testCount.setYes(2);
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);
        when(eventLogManagementServiceMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);

        mockMvc.perform(post("/meeting/rsvp").param("eventUid", dummyMeeting.getUid()).param("answer", "yes"))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("entityUid", is(dummyMeeting.getUid()))))
                .andExpect(model().attribute("responseTotals", hasProperty("yes", is(2))))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));

        verify(eventBrokerMock, times(2)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.YES);
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                                          Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);
        verify(eventLogManagementServiceMock, times(1)).getResponseCountForEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);
        verifyZeroInteractions(groupBrokerMock);

    }

    @Test
    public void changeReminderSettingShouldWork() throws Exception {
        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        Meeting dummyMeeting = new Meeting("test meeting", oneDayAway,
                                           sessionTestUser, testGroup, "some place");
        dummyMeeting.setReminderType(EventReminderType.CUSTOM);
        dummyMeeting.setCustomReminderMinutes(60 * 24);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);

        mockMvc.perform(post("/meeting/reminder").param("eventUid", dummyMeeting.getUid())
                                .param("reminderType", EventReminderType.CUSTOM.name())
                                .param("custom_minutes", String.valueOf(60*24)))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("entityUid", is(dummyMeeting.getUid()))));

        verify(eventBrokerMock, times(2)).loadMeeting(dummyMeeting.getUid());
        verify(eventBrokerMock, times(1)).updateReminderSettings(sessionTestUser.getUid(), dummyMeeting.getUid(),
                                                                 EventReminderType.CUSTOM, 60 * 24);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    /**
     * Todo once fixed the manual reminder logic is fixed
     */

    /*@Test
    public void sendReminderWorks() throws Exception {
        Event dummyMeeting = Meeting.makeEmpty(sessionTestUser);
        dummyMeeting.setId(dummyId);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.sendManualReminder(dummyMeeting, "")).thenReturn(true);
        mockMvc.perform(post("/meeting/remind").param("entityId", String.valueOf(dummyId))).andExpect(view()
                .name("redirect:/home")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/home"));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyMeeting.getId());
        verify(eventManagementServiceMock, times(1)).sendManualReminder(dummyMeeting, "");
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }*/

    /**
     * Commenting out the next two tests until free form is back working again
     */

    /*@Test
    public void testSendFreeFormWorksWithGroupSpecified() throws Exception {
        Group testGroup = new Group("", sessionTestUser);
        testGroup.setId(dummyId);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("testGroup", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("groupSpecified", is(true)));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }


    @Test
    public void sendFreeFormWorksWithGroupNotSpecified() throws Exception {
        Set<Group> dummyGroups = new HashSet<>();
        Group dummyGroup = new Group("", sessionTestUser);
        dummyGroup.setId(dummyId);
        dummyGroups.add(dummyGroup);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(dummyGroups);
        mockMvc.perform(get("/meeting/free"))
                .andExpect(status().isOk())
                .andExpect((view().name("meeting/free")))
                .andExpect(model().attribute("userGroups", hasItem(dummyGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));
        verify(permissionBrokerMock, times(2)).getActiveGroups(sessionTestUser, null);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void sendFreeMsgWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        dummyGroup.setId(dummyId);
        Event testEvent = Meeting.makeEmpty(sessionTestUser);
        testEvent.setId(dummyId);
        boolean includeSubGroups = true;
        String message = "message";
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(eventManagementServiceMock.sendManualReminder(testEvent, message)).thenReturn(true);
        mockMvc.perform(post("/meeting/free").param("confirmed", "").param("entityId", String.valueOf(dummyId))
                .param("message", message).param("includeSubGroups", String.valueOf(includeSubGroups)))
                .andExpect(view().name("redirect:/testGroup/view"))
                .andExpect(model().attribute("groupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/testGroup/view?groupUid=" + dummyGroup.getUid()));//Not happy with this solution
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verify(eventManagementServiceMock, times(1)).sendManualReminder(testEvent, message);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }



    @Test
    public void sendFreeFormWorksWithGroupId() throws Exception {
        User testUser = new User("", "testUser");
        testUser.setId(dummyId);
        Group testGroup = new Group("", testUser);
        testGroup.setId(dummyId);
        testUser.setId(dummyId);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("testGroup", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("groupSpecified", is(true)));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void sendFreeFormWorksWithoutGroupId() throws Exception{
        Group testGroup = new Group("",sessionTestUser);
        Set<Group> dummyGroups = Collections.singleton(testGroup);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(dummyGroups);

        testGroup.addMember(sessionTestUser);

        mockMvc.perform(get("/meeting/free")).andExpect(status().isOk())
                .andExpect(view().name("meeting/free")).andExpect(model()
                .attribute("userGroups", hasItem(testGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));
        verify(permissionBrokerMock, times(1)).getActiveGroups(sessionTestUser, null);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }*/
}



