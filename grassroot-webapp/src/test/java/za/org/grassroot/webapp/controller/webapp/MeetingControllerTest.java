package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;

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
    private static final Long dummyId = 1L;

    @InjectMocks
    private MeetingController meetingController;

    @Before
    public void setUp() {
        setUp(meetingController);
    }

     @Test
    public void shouldShowMeetingDetails() throws Exception {

         Event dummyMeeting = null;

         Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        dummyMeeting.setId(dummyId);
         dummyMeeting.setAppliesToGroup(dummyGroup);

        List<User> listOfDummyYesResponses = new ArrayList<>();
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(
                dummyMeeting);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                             Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)).thenReturn(true);
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
    public void testCreateMeetingWorks() throws Exception {
        Event dummyMeeting = null;
        dummyMeeting.setId(1L);

        Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        dummyGroup.setId(dummyId);
      //  when(groupManagementServiceMock.canUserCallMeeting(dummyId, sessionTestUser)).thenReturn(true);
        mockMvc.perform(post("/meeting/create").sessionAttr("meeting", dummyMeeting)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("selectedGroupId",
                        String.valueOf(dummyId))).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));
        // change below to permission broker
        // verify(groupManagementServiceMock, times(1)).canUserCallMeeting(dummyId, sessionTestUser);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(userManagementServiceMock);
    }

    @Test
    public void testSendFreeFormWorksWithGroupSpecified() throws Exception {
        Group testGroup = new Group("", sessionTestUser);
        testGroup.setId(dummyId);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
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
        mockMvc.perform(get("/meeting/free")).andExpect(status()
                .isOk()).andExpect((view().name("meeting/free"))
        ).andExpect(model().attribute("userGroups", hasItem(dummyGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));
        verify(permissionBrokerMock, times(2)).getActiveGroups(sessionTestUser, null);
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
//        Event dummyMeeting = new Event();
        Event dummyMeeting = null; // todo: new design?
        dummyMeeting.setId(dummyId);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);
//        when(eventManagementServiceMock.createMeeting(sessionTestUser)).thenReturn(dummyMeeting);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/create").param("groupId", String.valueOf(dummyId)))
                .andExpect((view().name("meeting/create"))).andExpect(status().isOk())
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("meeting",
                        hasProperty("id", is(dummyId)))).andExpect(model().attribute("groupSpecified", is(true)))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));
//        verify(eventManagementServiceMock, times(1)).createMeeting(sessionTestUser);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void createMeetingIndexWorksWithGroupNotSpecified() throws Exception {
        Set<Group> dummyGroups = new HashSet<>();
        Group dummyGroup = new Group("", sessionTestUser);
        dummyGroup.setId(dummyId);
        dummyGroups.add(dummyGroup);
        Event dummyMeeting = null;
        dummyMeeting.setId(dummyId);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(sessionTestUser);
//        when(eventManagementServiceMock.createMeeting(sessionTestUser)).thenReturn(dummyMeeting);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser,
                                                  Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(dummyGroups);

        mockMvc.perform(get("/meeting/create")).andExpect(status().isOk())
                .andExpect((view().name("meeting/create")))
                .andExpect(model().attribute("groupSpecified", is(false)))
                .andExpect(model().attribute("userGroups", hasItem(dummyGroup)));

//        verify(eventManagementServiceMock, times(1)).createMeeting(sessionTestUser);
        verify(permissionBrokerMock, times(1)).getActiveGroups(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void sendFreeMsgWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group3", new User("234345345"));
        dummyGroup.setId(dummyId);
        Event testEvent = null;
        testEvent.setId(dummyId);
        boolean includeSubGroups = true;
        String message = "message";
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(eventManagementServiceMock.sendManualReminder(testEvent, message)).thenReturn(true);
        mockMvc.perform(post("/meeting/free").param("confirmed", "").param("entityId", String.valueOf(dummyId))
                .param("message", message).param("includeSubGroups", String.valueOf(includeSubGroups)))
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(model().attribute("groupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group/view?groupUid=" + dummyGroup.getUid()));//Not happy with this solution
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verify(eventManagementServiceMock, times(1)).sendManualReminder(testEvent, message);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void initiateMeetingModificationWorks() throws Exception {
        Event dummyMeeting = null;
        dummyMeeting.setId(dummyId);
        Group testGroup = new Group("tg1", sessionTestUser);
        testGroup.setId(dummyId);
        dummyMeeting.setAppliesToGroup(testGroup);
        List<User> listOfDummyYesResponses = Arrays.asList(new User("", "testUser"));
        // when(groupManagementServiceMock.canUserCallMeeting(dummyId, sessionTestUser)).thenReturn(true);
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
      //  verify(groupManagementServiceMock, times(1)).canUserCallMeeting(dummyId, sessionTestUser);
        verifyZeroInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }


    @Test
    public void rsvpNoShouldWork() throws Exception {
        Event dummyMeeting = null;
        dummyMeeting.setId(dummyId);
        EventLog dummyEventLog = new EventLog();
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(sessionTestUser);
        // todo: new design?
/*
        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.NO))
                .thenReturn(dummyEventLog);
*/
        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("answer", "no"))
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
        Event dummyMeeting = null;
        dummyMeeting.setId(dummyId);
        EventLog dummyEventLog = new EventLog();
        List<User> listOfDummyYesResponses = new ArrayList<>();
        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(sessionTestUser, EventRSVPResponse.YES);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(dummyMeeting))
                .thenReturn(listOfDummyYesResponses);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        // todo: new design?
/*
        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.YES))
                .thenReturn(dummyEventLog);
*/
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);
        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("answer", "yes"))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));
        verify(eventManagementServiceMock, times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(2)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, sessionTestUser, EventRSVPResponse.YES);
        verify(eventManagementServiceMock, times(1)).getRSVPResponses(dummyMeeting);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);

    }

    @Test
    public void sendReminderWorks() throws Exception {
        Event dummyMeeting = null;
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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
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
    }
}



