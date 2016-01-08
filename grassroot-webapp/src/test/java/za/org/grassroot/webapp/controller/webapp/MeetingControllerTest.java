package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

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


    @InjectMocks
    private MeetingController meetingController;


    @Before
    public void setUp() {
        setUp(meetingController);


    }


    @Test
    public void shouldShowMeetingDetails() throws Exception {

        long eventId = 1L;
        Event dummyMeeting = new Event();
        dummyMeeting.setId(eventId);

        User dummyUser = new User("", "testUser");
        dummyUser.setId(1L);
        List<User> listOfDummyYesResponses = mock(List.class);

        HashMap<User, EventRSVPResponse> dummyResponsesMap = mock(HashMap.class);
        dummyResponsesMap.put(dummyUser, EventRSVPResponse.INVALID_RESPONSE.YES);

        Set<Map.Entry<User, EventRSVPResponse>> dummyRsvpResponses =
                dummyResponsesMap.entrySet();


        when(eventManagementServiceMock.loadEvent(eventId)).thenReturn(
                dummyMeeting);
        when(eventManagementServiceMock.getListOfUsersThatRSVPYesForEvent(
                dummyMeeting)).thenReturn(listOfDummyYesResponses);
        when(eventManagementServiceMock.getRSVPResponses(dummyMeeting)).
                thenReturn(dummyResponsesMap);

        when(listOfDummyYesResponses.size()).thenReturn(1);
        when(dummyResponsesMap.entrySet()).thenReturn(dummyRsvpResponses);

        mockMvc.perform(get("/meeting/view").param("eventId",
                String.valueOf(eventId))).andExpect(status().isOk())
                .andExpect(view().name("meeting/view"))
                .andExpect(model().attribute("meeting", hasProperty("id", is(1L))))
                .andExpect(model().attribute("rsvpYesTotal", equalTo(1)));


        verify(eventManagementServiceMock, times(1)).loadEvent(eventId); //
        verify(eventManagementServiceMock, //
                times(1)).getListOfUsersThatRSVPYesForEvent(dummyMeeting); //
        verify(eventManagementServiceMock, //
                times(1)).getRSVPResponses(dummyMeeting);

    }


   /* @Test
    public void TestCreateMeetingWorks() throws Exception {

        Event dummyMeeting = new Event();

        long selectedGroupId = 1L;

        dummyMeeting.setId(1L);


        when(eventManagementServiceMock.updateEvent(dummyMeeting)).thenReturn(
                dummyMeeting);
        when(eventManagementServiceMock.setGroup(dummyMeeting,
                selectedGroupId)).thenReturn(dummyMeeting);

        mockMvc.perform(post("/meeting/create").param("selectedGroupId",
                String.valueOf(selectedGroupId))).andExpect(status().isOk())
                .andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home"));


    }*/

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
        verifyNoMoreInteractions(groupManagementServiceMock);

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

        mockMvc.perform(get("/meeting/free").with(user(testUserPhone))).andExpect((view().name("meeting/free"))
        ).andExpect(model().attribute("userGroups", hasItems(dummyGroup)))
                .andExpect(model().attribute("groupSpecified", is(false)));

        verify(groupManagementServiceMock, times(1)).getGroupsPartOf(testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

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
        List<String[]> minuteOptions = mock(List.class);

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUser)).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.loadGroup(id)).thenReturn(testGroup);
        when(eventManagementServiceMock.setGroup(dummyMeeting.getId(), id)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);

        mockMvc.perform(get("/meeting/create").with(user(testUser)).param("groupId", String.valueOf(id)))
                .andExpect((view().name("meeting/create")))
                .andExpect(model().attribute("group", hasProperty("id", is(id)))).andExpect(model().attribute("meeting",
                hasProperty("id", is(id)))).andExpect(model().attribute("groupSpecified", is(true)));
        //  .andExpect(model().attribute("reminderOptions", hasItem(minuteOptions)));

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

        List<Group> dummyGroups = Arrays.asList(
                new Group("", testUser), new Group("", testUser),
                new Group("", testUser));
       // dummyGroups.add(testGroup);
        Event dummyMeeting = new Event();
        dummyMeeting.setId(1L);
        List<String[]> minuteOptions = mock(List.class);

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.createMeeting(testUser)).thenReturn(dummyMeeting);
        when(eventManagementServiceMock.setEventNoReminder(dummyMeeting.getId())).thenReturn(dummyMeeting);
        when(groupManagementServiceMock.getGroupsPartOf(testUser)).thenReturn(
                dummyGroups);

        mockMvc.perform(get("/meeting/create").with(user(testUserPhone))).andExpect((view().name("meeting/create"))
        ).andExpect(model().attribute("groupSpecified", is(false)))
              //  .andExpect(model().attribute("userGroups", hasItems(dummyGroups)))
                .andExpect(model().attribute("meeting", hasProperty("id", is (1L))));

        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(eventManagementServiceMock,times(1)).createMeeting(testUser);
        verify(eventManagementServiceMock, times (1)).setEventNoReminder(dummyMeeting.getId());
        verify(groupManagementServiceMock, times(1)).getGroupsPartOf(testUser);




    }

     @Test
    public void sendFreeMsgWorks() throws Exception{

         Long id =1L;
         Group dummyGroup = new Group();
         dummyGroup.setId(id);
         Event testEvent = new Event();
         testEvent.setId(id);
         User testUser = new User("","testUser");
         boolean includeSubGroup = true;
         String message ="message";


         when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
         when(groupManagementServiceMock.loadGroup(id)).thenReturn(dummyGroup);
         when(eventManagementServiceMock.createEvent("",testUser,dummyGroup,includeSubGroup)).thenReturn(testEvent);
         when(eventManagementServiceMock.sendManualReminder(testEvent,message)).thenReturn(true);









     }


/*
   @Test
    public void rsvpNoShouldWork() throws Exception {

    }

    @Test
    public void rsvpYesShouldWork() throws Exception {

    }

    @Test
    public void sendRemiderShoudWork() {

    }
*/
}


// }
