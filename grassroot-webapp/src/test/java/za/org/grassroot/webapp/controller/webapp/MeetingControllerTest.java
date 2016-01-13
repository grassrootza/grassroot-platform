package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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
    public void shouldShowMeetingDetails() throws Exception {

        long id = 1L;
        Event dummyMeeting = new Event();
        dummyMeeting.setId(id);

        User dummyUser = new User("", "testUser");
        dummyUser.setId(id);
        List<User> listOfDummyYesResponses = new ArrayList<>();

        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(dummyUser, EventRSVPResponse.YES);


        when(eventManagementServiceMock.loadEvent(id)).thenReturn(
                dummyMeeting);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(
                dummyMeeting)).thenReturn(listOfDummyYesResponses);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).
                thenReturn(dummyResponsesMap);


        mockMvc.perform(get("/meeting/view").param("eventId",
                String.valueOf(id))).andExpect(status().isOk())
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(id))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));


        verify(eventManagementServiceMock, times(1)).loadEvent(id);
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
        Group dummyGroup = new Group();

        Long dummyId = 1L;
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


        Long groupId = 1L;
        User testUser = new User("", "testuser");
        Group testGroup = new Group("", testUser);
        testGroup.setId(groupId);

        when(groupManagementServiceMock.loadGroup(groupId)).thenReturn(testGroup);

        mockMvc.perform(get("/meeting/free").with(user(testUserPhone)).param("groupId", String.valueOf(groupId)))
                .andExpect(status().isOk())
                .andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(groupId))))
                .andExpect(model().attribute("groupSpecified", is(true)));

        verify(groupManagementServiceMock, times(1)).loadGroup(groupId);
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }


    @Test

    public void sendFreeFormWorksWithGroupNotSpecified() throws Exception {


        User testUser = new User("", "testUser");
        ArrayList<Group> dummyGroups = new ArrayList<>();
        Group dummyGroup = new Group("", testUser);
        dummyGroup.setId(1L);
        dummyGroups.add(dummyGroup);


        when(groupManagementServiceMock.getGroupsPartOf(testUser)).thenReturn(
                dummyGroups);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);

        mockMvc.perform(get("/meeting/free").with(user(testUserPhone))).andExpect(status()
                .isOk()).andExpect((view().name("meeting/free"))
        ).andExpect(model().attribute("userGroups", hasItem(dummyGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));

        verify(groupManagementServiceMock, times(1)).getGroupsPartOf(testUser);
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verifyZeroInteractions(eventManagementServiceMock);
        verifyZeroInteractions(eventLogManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }


    @Test
    public void createMeetingIndexWorksWithGroupSpecified() throws Exception {


        long id = 1L;
        User testUser = new User("", "testUser");
        Group testGroup = new Group("", testUser);
        testGroup.setId(id);
        testUser.setId(id);

        Event dummyMeeting = new Event();
        dummyMeeting.setId(id);

        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);


        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUser)).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.loadGroup(id)).thenReturn(testGroup);
        when(eventManagementServiceMock.setGroup(dummyMeeting.getId(), id)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);


        mockMvc.perform(get("/meeting/create").with(user(testUserPhone)).param("groupId", String.valueOf(id)))
                .andExpect((view().name("meeting/create"))).andExpect(status().isOk())
                .andExpect(model().attribute("group", hasProperty("id", is(id))))
                .andExpect(model().attribute("meeting",
                        hasProperty("id", is(id)))).andExpect(model().attribute("groupSpecified", is(true)))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));

        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(eventManagementServiceMock, times(1)).createMeeting(testUser);
        verify(groupManagementServiceMock, times(1)).loadGroup(id);
        verify(eventManagementServiceMock, times(1)).setGroup(dummyMeeting.getId(), id);
        verify(eventManagementServiceMock, times(1)).setEventNoReminder(dummyMeeting.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void createMeetingIndexWorksWithGroupNotSpecified() throws Exception {

        User testUser = new User("", "testUser");
        testUser.setId(1L);


        ArrayList<Group> dummyGroups = new ArrayList<>();
        Group dummyGroup = new Group("", testUser);
        dummyGroup.setId(1L);
        dummyGroups.add(dummyGroup);

        Event dummyMeeting = new Event();
        dummyMeeting.setId(1L);
        List<String[]> minuteOptions = new ArrayList<>();
        String[] oneDay = new String[]{"" + 24 * 60, "One day ahead"};
        minuteOptions.add(oneDay);

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUser)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.getGroupsPartOf(testUser)).thenReturn(
                dummyGroups);

        mockMvc.perform(get("/meeting/create").with(user(testUserPhone))).andExpect(status().isOk())
                .andExpect((view().name("meeting/create"))
                ).andExpect(model().attribute("groupSpecified", is(false)))
                .andExpect(model().attribute("userGroups", hasItems(dummyGroup)))
                .andExpect(model().attribute("meeting", hasProperty("id", is(1L))))
                .andExpect(model().attribute("reminderOptions", hasItem(oneDay)));

        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(eventManagementServiceMock, times(1)).createMeeting(testUser);
        verify(eventManagementServiceMock, times(1)).setEventNoReminder(dummyMeeting.getId());
        verify(groupManagementServiceMock, times(1)).getGroupsPartOf(testUser);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);


    }

    @Test
    public void sendFreeMsgWorks() throws Exception {


        Group dummyGroup = new Group();
        dummyGroup.setId(dummyId);
        Event testEvent = new Event();
        testEvent.setId(dummyId);
        User testUser = new User("", "testUser");
        boolean includeSubGroups = true;
        String message = "message";


        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        when(eventManagementServiceMock.createEvent("", testUser, dummyGroup, includeSubGroups)).thenReturn(testEvent);
        when(eventManagementServiceMock.sendManualReminder(testEvent, message)).thenReturn(true);


        mockMvc.perform(post("/meeting/free").param("confirmed", "").param("entityId", String.valueOf(dummyId))
                .param("message", message).param("includeSubGroups", String.valueOf(includeSubGroups)))
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(model().attribute("groupId", String.valueOf(dummyId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group/view?groupId=1"));//Not happy with this solution

        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(eventManagementServiceMock, times(1)).createEvent("", testUser, dummyGroup, includeSubGroups);
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

        User testUser = new User("", "testUser");
        EventLog dummyEventLog = new EventLog();


        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);

        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, testUser, EventRSVPResponse.NO))
                .thenReturn(dummyEventLog);
        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("no", ""))
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));

        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, testUser, EventRSVPResponse.NO);
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);

        verifyZeroInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);


    }


    @Test
    public void rsvpYesShouldWork() throws Exception {

        Event dummyMeeting = new Event();
        dummyMeeting.setId(dummyId);

        User testUser = new User("", "testUser");
        EventLog dummyEventLog = new EventLog();

        List<User> listOfDummyYesResponses = new ArrayList<>();

        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(testUser, EventRSVPResponse.YES);

        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(dummyMeeting))
                .thenReturn(listOfDummyYesResponses);

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(dummyMeeting);
        when(eventLogManagementServiceMock.rsvpForEvent(dummyMeeting, testUser, EventRSVPResponse.YES))
                .thenReturn(dummyEventLog);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).thenReturn(dummyResponsesMap);


        mockMvc.perform(post("/meeting/rsvp").param("eventId", String.valueOf(dummyId)).param("yes", ""))
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(listOfDummyYesResponses.size())))
                .andExpect(model().attribute("rsvpResponses", hasItems(dummyResponsesMap.entrySet().toArray())));
        ;

        verify(eventManagementServiceMock, times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting);
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(dummyMeeting, testUser, EventRSVPResponse.YES);
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
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

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);

        mockMvc.perform(get("/meeting/free").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("meeting/free"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))))
                .andExpect(model().attribute("groupSpecified", is(true)));

    }

}


// }
