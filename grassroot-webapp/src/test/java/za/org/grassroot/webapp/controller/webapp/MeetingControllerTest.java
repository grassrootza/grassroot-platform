package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



/*
 * Paballo Ditshego 06/01/2016
 */
public class MeetingControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);
    private static final Long dummyId = 1L;

    @InjectMocks
    private MeetingController meetingController;

    @Before
    public void setUp() {
        setUp(meetingController);
    }

     @Test
    public void shouldShowMeetingDetails() throws Exception{
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        List<User> listOfDummyYesResponses = new ArrayList<>();
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(
                dummyMeeting);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(
                dummyMeeting)).thenReturn(listOfDummyYesResponses);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).
                thenReturn(dummyResponsesMap);
        mockMvc.perform(get("/meeting/view").param("eventId",
                String.valueOf(dummyId))).andExpect(status().isOk())
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventManagementServiceMock,
                times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting);
        verify(eventManagementServiceMock, //
                times(1)).getRSVPResponses(dummyMeeting);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(userManagementServiceMock);
    }


    @Test
    public void TestCreateMeetingWorks() throws Exception {
        Event dummyMeeting = new Event();
        dummyMeeting.setId(1L);
        Group dummyGroup = new Group();;
        dummyGroup.setId(dummyId);
        when(eventManagementServiceMock.updateEvent(dummyMeeting)).thenReturn(
                dummyMeeting);
        when(eventManagementServiceMock.setGroup(dummyMeeting.getId(),
                dummyId)).thenReturn(dummyMeeting);
        mockMvc.perform(post("/meeting/create").sessionAttr("meeting", dummyMeeting)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("selectedGroupId",
                        String.valueOf(dummyId))).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));
        verify(eventManagementServiceMock, times(1)).updateEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(1)).setGroup(dummyId, dummyId);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void TestSendFreeFormWorksWithGroupSpecified() throws Exception {
        Group testGroup = new Group("", sessionTestUser);
        testGroup.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("groupSpecified", is(true)));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }


    @Test
    public void sendFreeFormWorksWithGroupNotSpecified() throws Exception {
        ArrayList<Group> dummyGroups = new ArrayList<>();
        Group dummyGroup = new Group("", sessionTestUser);
        dummyGroup.setId(dummyId);
        dummyGroups.add(dummyGroup);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(
                dummyGroups);
        mockMvc.perform(get("/meeting/free")).andExpect(status()
                .isOk()).andExpect((view().name("meeting/free"))
        ).andExpect(model().attribute("userGroups", hasItem(dummyGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));
        verify(groupManagementServiceMock, times(1)).getActiveGroupsPartOf(sessionTestUser);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }


    @Test
    public void createMeetingIndexWorksWithGroupSpecified() throws Exception {
        Group testGroup = new Group("", sessionTestUser);
        testGroup.setId(dummyId);
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);
        when(eventManagementServiceMock.createMeeting(sessionTestUser)).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(eventManagementServiceMock.setGroup(dummyMeeting.getId(), dummyId)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);
        mockMvc.perform(get("/meeting/create").param("groupId", String.valueOf(dummyId)))
                .andExpect((view().name("meeting/create"))).andExpect(status().isOk())
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("meeting",
                        hasProperty("id", is(dummyId)))).andExpect(model().attribute("groupSpecified", is(true)))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));
        verify(eventManagementServiceMock, times(1)).createMeeting(sessionTestUser);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(eventManagementServiceMock, times(1)).setGroup(dummyMeeting.getId(), dummyId);
        verify(eventManagementServiceMock, times(1)).setEventNoReminder(dummyMeeting.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void createMeetingIndexWorksWithGroupNotSpecified() throws Exception {
        ArrayList<Group> dummyGroups = new ArrayList<>();
        Group dummyGroup = new Group();
        dummyGroup.addMember(sessionTestUser);
        dummyGroup.setId(dummyId);
        dummyGroups.add(dummyGroup);
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);
        when(eventManagementServiceMock.createMeeting(sessionTestUser)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(
                dummyGroups);
        mockMvc.perform(get("/meeting/create")).andExpect(status().isOk())
                .andExpect((view().name("meeting/create"))
                ).andExpect(model().attribute("groupSpecified", is(false)))
                .andExpect(model().attribute("userGroups", hasItem(dummyGroup)))
                .andExpect(model().attribute("meeting", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));
        verify(eventManagementServiceMock, times(1)).createMeeting(sessionTestUser);
        verify(eventManagementServiceMock, times(1)).setEventNoReminder(dummyMeeting.getId());
        verify(groupManagementServiceMock, times(1)).getActiveGroupsPartOf(sessionTestUser);

        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void sendFreeMsgWorks() throws Exception {
        Group dummyGroup = new Group();
        dummyGroup.setId(dummyId);
        Event testEvent = new Event();
        testEvent.setId(dummyId);
        boolean includeSubGroups = true;
        String message = "message";
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        when(eventManagementServiceMock.createEvent("", sessionTestUser, dummyGroup, includeSubGroups)).thenReturn(testEvent);
        when(eventManagementServiceMock.sendManualReminder(testEvent, message)).thenReturn(true);
        mockMvc.perform(post("/meeting/free").param("confirmed", "").param("entityId", String.valueOf(dummyId))
                .param("message", message).param("includeSubGroups", String.valueOf(includeSubGroups)))
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(model().attribute("groupId", String.valueOf(dummyId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group/view?groupId=1"));//Not happy with this solution
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(eventManagementServiceMock, times(1)).createEvent("", sessionTestUser, dummyGroup, includeSubGroups);
        verify(eventManagementServiceMock, times(1)).sendManualReminder(testEvent, message);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);


    }

    @Test
    public void initiateMeetingModificationWorks() throws Exception {
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        List<User> listOfDummyYesResponses = Arrays.asList(new User("", "testUser"));
        when(eventManagementServiceMock.loadEvent(dummyMeeting.getId())).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(dummyMeeting)).thenReturn(listOfDummyYesResponses);
        mockMvc.perform(post("/meeting/modify").param("change", "true").sessionAttr("meeting", dummyMeeting)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isOk()).
                andExpect(model().attribute("meeting",
                        hasProperty("id", is(dummyId)))).andExpect(model()
                .attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(view().name("meeting/modify"));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyMeeting.getId());
        verify(eventManagementServiceMock, times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting);
        verifyZeroInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }


    @Test
    public void rsvpNoShouldWork() throws Exception {
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        EventLog dummyEventLog = new EventLog();
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(sessionTestUser);
        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.NO))
                .thenReturn(dummyEventLog);
        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("no", ""))
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.NO);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);
    }


    @Test
    public void rsvpYesShouldWork() throws Exception {
        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);
        EventLog dummyEventLog = new EventLog();
        List<User> listOfDummyYesResponses = new ArrayList<>();
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(dummyMeeting))
                .thenReturn(listOfDummyYesResponses);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.YES))
                .thenReturn(dummyEventLog);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);
        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("yes", ""))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));
        verify(eventManagementServiceMock, times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.YES);
        verify(eventManagementServiceMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);

    }


    @Test
    public void sendReminderWorks() throws Exception {
        Event dummyMeeting = new Event();
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

    }


    @Test
    public void sendFreeFormWorksWithGroupId() throws Exception {
        User testUser = new User("", "testUser");
        testUser.setId(dummyId);
        Group testGroup = new Group("", testUser);
        testGroup.setId(dummyId);
        testUser.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("groupSpecified", is(true)));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void sendFreeFormWorksWithoutGroupId() throws Exception{
        Group testGroup = new Group("",sessionTestUser);
        List<Group> dummyGroups = Arrays.asList(new Group("", sessionTestUser));
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(dummyGroups);
        mockMvc.perform(get("/meeting/free")).andExpect(status().isOk())
                .andExpect(view().name("meeting/free")).andExpect(model()
                .attribute("userGroups", hasItem(testGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));

        verify(groupManagementServiceMock, times(1)).getActiveGroupsPartOf(sessionTestUser);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }
}



