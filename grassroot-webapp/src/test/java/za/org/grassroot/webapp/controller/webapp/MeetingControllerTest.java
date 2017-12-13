package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.MeetingImportance;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.webapp.model.web.MeetingWrapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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

    @Mock
    private TaskImageBroker taskImageBrokerMock;

    @InjectMocks
    private MeetingController meetingController;

    @Before
    public void setUp() {
        setUp(meetingController);
    }

    @Test
    public void shouldShowMeetingDetails() throws Exception {

        Group dummyGroup = new Group("Dummy Group3", new User("234345345", null, null));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(dummyGroup).setEventLocation("some place").createMeeting();

         logger.info("ZOG: dummyMeetingIs: {} ", dummyMeeting);

        ResponseTotalsDTO testCount = ResponseTotalsDTO.makeForTest(1, 0, 0, 0, 1);
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);
        when(eventLogBrokerMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);
        when(eventBrokerMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);

        mockMvc.perform(get("/meeting/view").param("eventUid", dummyMeeting.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("uid", is(dummyMeeting.getUid()))))
                .andExpect(model().attribute("responseTotals", hasProperty("yes", is(1))))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));

        // don't need first one anymore as checks for meeting creator
        //verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS);
        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogBrokerMock, times(1)).getResponseCountForEvent(dummyMeeting);
        verify(eventBrokerMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void testCreateMeetingWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group3", new User("234345345", null, null));
        LocalDateTime tomorrow = LocalDateTime.now().plus(1, ChronoUnit.DAYS);
        MeetingWrapper wrapper = MeetingWrapper.makeEmpty(EventReminderType.GROUP_CONFIGURED, 60);
        wrapper.setTitle("test meeting");
        wrapper.setEventDateTime(tomorrow);
        wrapper.setLocation("some place");
        wrapper.setDescription("This is a description");

        mockMvc.perform(post("/meeting/create").sessionAttr("meeting", wrapper)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("selectedGroupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(redirectedUrl("/group/view?groupUid=" + dummyGroup.getUid()));

        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(sessionTestUser.getUid())
                .parentType(JpaEntityType.GROUP)
                .parentUid(dummyGroup.getUid())
                .name("test meeting")
                .startDateTime(tomorrow)
                .location("some place")
                .reminderType(EventReminderType.GROUP_CONFIGURED)
                .customReminderMinutes(60)
                .description("This is a description")
                .importance(MeetingImportance.ORDINARY)
                .assignedMemberUids(new HashSet<>());

        verify(eventBrokerMock, times(1)).createMeeting(helper);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void createMeetingIndexWorksWithGroupSpecified() throws Exception {

        Group testGroup = new Group("", sessionTestUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        when(permissionBrokerMock.countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(1);

        mockMvc.perform(get("/meeting/create")
                .param("groupUid", testGroup.getUid()))
                .andExpect((view().name("meeting/create")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid()))));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void createMeetingIndexWorksWithGroupNotSpecified() throws Exception {

        Group dummyGroup = new Group("", sessionTestUser);
        List<Group> dummyGroups = Collections.singletonList(dummyGroup);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(1);
        when(permissionBrokerMock.getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(dummyGroups);

        mockMvc.perform(get("/meeting/create")).andExpect(status().isOk())
                .andExpect((view().name("meeting/create")))
                .andExpect(model().attribute("userGroups", hasItem(dummyGroup)));

        verify(userManagementServiceMock, times(1)).load(sessionTestUser.getUid());
        verify(permissionBrokerMock, times(1)).getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
    }

    @Test
    public void meetingModificationWorks() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(testGroup).setEventLocation("some place").createMeeting();

        LocalDateTime dateTime = LocalDateTime.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);

        ResponseTotalsDTO testCount = ResponseTotalsDTO.makeForTest(1, 0, 0, 0, 1);
        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);

        when(eventLogBrokerMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);

        mockMvc.perform(post("/meeting/modify")
                .param("eventUid", dummyMeeting.getUid())
                .param("location", "some place")
                .param("eventDateTime", DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a").format(dateTime))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("meeting", hasProperty("uid", is(dummyMeeting.getUid()))))
                .andExpect(model().attribute("responseTotals", hasProperty("yes", is(1))))
                .andExpect(view().name("meeting/view"));

        verify(eventBrokerMock, times(1)).updateMeeting(sessionTestUser.getUid(), dummyMeeting.getUid(), null,
                                                        dateTime, "some place");

        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventBrokerMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(eventBrokerMock);
        verify(eventLogBrokerMock, times(1)).getResponseCountForEvent(dummyMeeting);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void rsvpNoShouldWork() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(testGroup).setEventLocation("some place").createMeeting();

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);

        mockMvc.perform(post("/meeting/rsvp").header("referer", "/home").param("eventUid", dummyMeeting.getUid()).param("answer", "no"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));

        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogBrokerMock, times(1)).rsvpForEvent(dummyMeeting.getUid(), sessionTestUser.getUid(), EventRSVPResponse.NO);
        verifyZeroInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(eventLogBrokerMock);
    }


    @Test
    public void rsvpYesShouldWork() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(testGroup).setEventLocation("some place").createMeeting();

        ResponseTotalsDTO testCount = ResponseTotalsDTO.makeForTest(2, 0, 0, 0, 2);
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                             Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS)).thenReturn(true);
        when(eventLogBrokerMock.getResponseCountForEvent(dummyMeeting)).thenReturn(testCount);
        when(eventBrokerMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);

        mockMvc.perform(post("/meeting/rsvp").header("referer", "/home").param("eventUid", dummyMeeting.getUid()).param("answer", "yes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));

        verify(eventBrokerMock, times(1)).loadMeeting(dummyMeeting.getUid());
        verify(eventLogBrokerMock, times(1)).rsvpForEvent(dummyMeeting.getUid(), sessionTestUser.getUid(), EventRSVPResponse.YES);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(eventLogBrokerMock);
        verifyZeroInteractions(groupBrokerMock);

    }

    @Test
    public void changeReminderSettingShouldWork() throws Exception {
        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(testGroup).setEventLocation("some place").createMeeting();
        dummyMeeting.setReminderType(EventReminderType.CUSTOM);
        dummyMeeting.setCustomReminderMinutes(60 * 24);

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);

        mockMvc.perform(post("/meeting/reminder").param("eventUid", dummyMeeting.getUid())
                                .param("reminderType", EventReminderType.CUSTOM.name())
                                .param("custom_minutes", String.valueOf(60*24)))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("uid", is(dummyMeeting.getUid()))));

        verify(eventBrokerMock, times(2)).loadMeeting(dummyMeeting.getUid());
        verify(eventBrokerMock, times(1)).updateReminderSettings(sessionTestUser.getUid(), dummyMeeting.getUid(),
                                                                 EventReminderType.CUSTOM, 60 * 24);
        verify(eventBrokerMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

}



