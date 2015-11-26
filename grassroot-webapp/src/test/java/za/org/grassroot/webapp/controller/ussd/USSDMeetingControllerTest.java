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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/11/26.
 */
public class USSDMeetingControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDMeetingControllerTest.class);

    private static final String testUserPhone = "27601110000";
    private static final String phoneParam = "msisdn";

    private static final String path = "/ussd/mtg/";

    @InjectMocks
    USSDMeetingController ussdMeetingController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdMeetingController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        ussdMeetingController.setMessageSource(messageSource());

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
        when(eventManagementServiceMock.createEvent("", testUser)).thenReturn(dummyEvent);
        when(eventManagementServiceMock.setEventNoReminder(0L)).thenReturn(dummyEvent);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(eventManagementServiceMock, times(1)).getUpcomingEventsUserCreated(any(User.class));
        verify(eventManagementServiceMock, times(1)).createEvent(anyString(), any(User.class));

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
        when(eventManagementServiceMock.createEvent("", testUser)).thenReturn(dummyEvent);
        when(eventManagementServiceMock.setEventNoReminder(0L)).thenReturn(dummyEvent);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(groupPage);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(eventManagementServiceMock, times(1)).createEvent(anyString(), any(User.class));
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(any(User.class), anyInt(), anyInt());

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

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);

        mockMvc.perform(get(path + "newgroup").param(phoneParam, testUserPhone).param("eventId", "0")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    /*
    Series of tests, for a single method, the one in the guts of the meeting menu, which handles the creation of a group
    and addition of numbers to it. It is called as follows:

    public Request createGroup(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                               @RequestParam(value=EVENT_PARAM, required=true) Long eventId,
                               @RequestParam(value=GROUP_PARAM, required=false) Long groupId,
                               @RequestParam(value=TEXT_PARAM, required=false) String userResponse,
                               @RequestParam(value="prior_input", required=false) String priorInput,
                               HttpServletRequest request) throws URISyntaxException
    */

    @Test
    public void addingNumbersToNewGroupMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);
        when(groupManagementServiceMock.createNewGroup(testUser, Arrays.asList("0801112345"))).thenReturn(testGroup);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("request", "0801112345")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verify(groupManagementServiceMock, times(1)).createNewGroup(testUser, Arrays.asList("0801112345"));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void addingNumbersToExistingGroupMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);

        // note: deliberately pass a badly formed number and then check that only the well-formed one is passed to services
        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("request", "0801112345 080111234")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).addNumbersToGroup(testGroup.getId(), Arrays.asList("0801112345"));
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);

    }

    @Test
    public void enteringZeroToStopGroupCreationShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("request", "0")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setGroup(1L, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void enteringNoValidNumbersShouldGiveError() throws Exception {

        User testUser = new User(testUserPhone);

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);

        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("eventId", "1").
                param("request", "0")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString(), anyString());
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
        testGroup.setId(1L);
        String urlToCheck = "mtg/subject?eventId=1&menukey=group&prior_input=1";

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToCheck)).thenReturn(testUser);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("eventId", "1").
                param("groupId", "" + testGroup.getId()).param("menukey", "group")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToCheck);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setGroup(1L, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }

    @Test
    public void settingLocationShouldWork() throws Exception {


        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void settingDateTimeShouldWork() throws Exception {

        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void manageMeetingMenuShouldWork() {

    }

    @Test
    public void changeDateMenuShouldWork() {

    }

    @Test
    public void changeLocationPromptShouldWork() {

    }

    @Test
    public void cancelMeetingPromptShouldWork() {

    }

    @Test
    public void modifyNotificationShouldWork() {

    }

}
