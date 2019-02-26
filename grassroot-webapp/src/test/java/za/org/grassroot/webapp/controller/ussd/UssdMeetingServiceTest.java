package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.MeetingRequest;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.task.enums.EventListTimeType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.mockito.Mockito.*;
import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.webapp.controller.ussd.UssdAdvancedHomeServiceTest.testGroupName;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/11/26.
 */
public class UssdMeetingServiceTest extends UssdUnitTest {

    private static final Logger log = LoggerFactory.getLogger(UssdMeetingServiceTest.class);

    private static final String testUserPhone = "27601110000";
    private static final String phoneParam = "msisdn";
    private static final String path = "/ussd/mtg/";

    private UssdMeetingService ussdMeetingService;
    private UssdHomeService ussdHomeService;

    @Before
    public void setUp() {
//        mockMvc = MockMvcBuilders.standaloneSetup(ussdMeetingController, ussdHomeController)
//                .setHandlerExceptionResolvers(exceptionResolver())
//                .setValidator(validator())
//                .setViewResolvers(viewResolver())
//                .build();


//        wireUpHomeController(ussdHomeController);
        // todo: VJERAN fix this dep
//        wireUpMessageSourceAndGroupUtil(ussdMeetingController);
//        ussdHomeController.setMeetingController(ussdMeetingController);
//        ussdMeetingController.setEventUtil(ussdEventUtil);
//        ussdMeetingController.setGroupUtil(ussdGroupUtil);
//        ussdEventUtil.setMessageSource(messageSource());

        this.ussdMeetingService = new UssdMeetingServiceImpl(false, false, taskBrokerMock, ussdSupport, userManagementServiceMock, eventBrokerMock, eventRequestBrokerMock, eventLogBrokerMock, accountFeaturesBrokerMock, null, ussdEventUtil, ussdGroupUtil, groupBrokerMock, cacheUtilManagerMock);
        this.ussdHomeService = new UssdHomeServiceImpl(ussdSupport, null, null, null, null, ussdMeetingService, null, locationInfoBrokerMock, userManagementServiceMock, campaignBrokerMock, null, userLoggerMock, cacheUtilManagerMock, userResponseBrokerMock, groupQueryBrokerMock, accountFeaturesBrokerMock, groupBrokerMock);
    }

    @Test
    public void meetingRsvpShouldWorkInAllLanguages() throws Exception {
        User testUser = new User(testUserPhone, null, null);
        Group testGroup = new Group(testGroupName, GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        testGroup.addMember(testUser, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Meeting meeting = new MeetingBuilder().setName("Meeting about testing").setStartDateTime(Instant.now()).setUser(testUser).setParent(testGroup).setEventLocation("someLocation").createMeeting();

        List<User> groupMembers = new ArrayList<>(languageUsers);
        groupMembers.add(testUser);

        for (User user: groupMembers) {
            user.setHasInitiatedSession(false);
            TaskMinimalDTO taskDetails = new TaskMinimalDTO(meeting, Instant.now());

            when(userManagementServiceMock.loadOrCreateUser(user.getPhoneNumber(), UserInterfaceType.USSD)).thenReturn(user);
            when(userResponseBrokerMock.checkForEntityForUserResponse(user.getUid(), true)).thenReturn(meeting);
            when(taskBrokerMock.fetchDescription(user.getUid(), meeting.getUid(), TaskType.MEETING)).thenReturn(taskDetails);

            this.ussdHomeService.processStartMenu(user.getPhoneNumber(), null);

            verify(userResponseBrokerMock, times(1)).checkForEntityForUserResponse(user.getUid(), true);

            when(eventBrokerMock.loadMeeting(meeting.getUid())).thenReturn(meeting);

            this.ussdMeetingService.processRsvpAndWelcome(user.getPhoneNumber(), meeting.getUid(), "yes");

            verify(eventLogBrokerMock, times(1)).rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);
            verify(experimentBrokerMock, times(1)).recordEvent(eq("meeting_response"), eq(user.getUid()), eq(null), any(Map.class));
        }
    }

//    @Test
//    public void meetingStartMenuNoUpcomingMeetingsAndNoGroups() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(eventBrokerMock.userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE)).thenReturn(false);
//        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(0);
//
//        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventBrokerMock, times(1)).userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE);
//        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING);
//        verifyNoMoreInteractions(eventBrokerMock);
//
//    }

    @Test
    public void meetingStartMenuNoUpcomingMeetingsAndExistingGroups() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        List<Group> existingGroupList = Arrays.asList(new Group("gc1", GroupPermissionTemplate.DEFAULT_GROUP, testUser),
                                                      new Group("gc2", GroupPermissionTemplate.DEFAULT_GROUP, testUser),
                                                      new Group("gc3", GroupPermissionTemplate.DEFAULT_GROUP, testUser));
        existingGroupList.forEach(g -> g.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventBrokerMock.userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE)).thenReturn(false);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING)).thenReturn(1);
        when(permissionBrokerMock.getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING, 0, 3)).thenReturn(existingGroupList);

        this.ussdMeetingService.processMeetingOrg(testUserPhone, false);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_MEETING, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verify(eventBrokerMock, times(1)).userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE);
        verifyNoMoreInteractions(eventBrokerMock);

    }

    @Test
    public void meetingStartWithUpcomingMeetings() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Group group = new Group("someGroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        Instant startTime = Instant.now();

        List<Event> upcomingMeetingList = Arrays.asList(
                new MeetingBuilder().setName("meeting1").setStartDateTime(startTime).setUser(testUser).setParent(group).setEventLocation("someLocation").createMeeting(),
                new MeetingBuilder().setName("meeting2").setStartDateTime(startTime).setUser(testUser).setParent(group).setEventLocation("someLocation").createMeeting(),
                new MeetingBuilder().setName("meeting3").setStartDateTime(startTime).setUser(testUser).setParent(group).setEventLocation("someLocation").createMeeting());

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventBrokerMock.userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE)).thenReturn(true);
        when(eventBrokerMock.getEventsUserCanView(testUser, EventType.MEETING, EventListTimeType.FUTURE, 0, 3)).thenReturn(
                new PageImpl<>(upcomingMeetingList));

        this.ussdMeetingService.processMeetingOrg(testUserPhone, false);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(eventBrokerMock, times(1)).userHasEventsToView(testUser, EventType.MEETING, EventListTimeType.FUTURE);
    }

//    @Test
//    public void newGroupPromptShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        String urlToSave = USSDSection.MEETINGS.toPath() + "newgroup";
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "newgroup").param(phoneParam, testUserPhone)).andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verifyZeroInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//
//    }
//
//    /*
//    Series of tests, for a single method, the one in the guts of the meeting menu, which handles the creation of a group
//    and addition of numbers to it.
//
//    */
//
//    @Test
//    public void addingNumbersToNewGroupMenuShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("", testUser);
//        String firstUrlToSave = saveMenuUrlWithInput(thisSection, "group", "", "0801112345");
//        Set<MembershipInfo> members = ordinaryMember("0801112345");
//        members.addAll(organizer(testUser));
//        String secondUrlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupUid=" + testGroup.getUid(), "0801112345");
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, firstUrlToSave)).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, secondUrlToSave)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("request", "0801112345")).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + secondUrlToSave).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, firstUrlToSave);
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, secondUrlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801112345"),
//                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
//       // verifyNoMoreInteractions(groupBrokerMock);
//
//    }
//
//    @Test
//    public void addingNumbersToExistingGroupMenuShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("", testUser);
//        String numbersToInput = "0801112345 080111234";
//        String urlToSave = saveMenuUrlWithInput(thisSection, "group", "?groupUid=" + testGroup.getUid(), numbersToInput);
//        log.info("Testing adding numbers to existing group, with this Url to save ..." + urlToSave);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//
//        // note: deliberately pass a badly formed number and then check that only the well-formed one is passed to services
//        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupUid", "" + testGroup.getUid()).
//                param("request", numbersToInput)).andExpect(status().isOk());
//        // note: this call simulates interruption -- it may add number a second time (hence 2 calls below), but better safe
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(nullable(String.class), nullable(String.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(2)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801112345"),
//                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventRequestBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//
//    }

   /* @Test
    public void enteringZeroToStopGroupCreationShouldWork() throws Exception {

        User testUser = new User(testUserPhone);
        Group testGroup = new Group("", testUser);
        testGroup.setId(1L);
        Event testMeeting = new Event("unit test", testUser, testGroup);
        testMeeting.setId(1L);
        String urlToSave = saveMenuUrlWithInput(thisSection, "testGroup", "?groupId=" + testGroup.getId(), "0");
        String urlToSave2 = saveMenuUrlWithInput(thisSection, "testGroup",
                                                 "?groupId=" + testGroup.getId() + "&eventId=" + testMeeting.getId(), "0");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave2)).thenReturn(testUser);
        when(eventManagementServiceMock.createLogbook(testUser, 1L)).thenReturn(testMeeting);

        mockMvc.perform(get(path + "testGroup").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId()).
                param("request", "0")).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave2).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, urlToSave2);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave2);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).createLogbook(testUser, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);

    }*/

//    @Test
//    public void enteringNoValidNumbersShouldGiveError() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        String urlToSave = saveMenuUrlWithInput(thisSection, "group", "", "0");
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("request", "0")).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verifyZeroInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//
//    }
//
//    @Test
//    public void returningToComplexGroupMenuShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("gc1", testUser);
//
//        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), nullable(String.class))).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//
//        mockMvc.perform(get(path + "group").param(phoneParam, testUserPhone).param("groupUid", "" + testGroup.getUid())
//                                .param("prior_input", "0801112345 080111234")).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class), nullable(String.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801112345"),
//                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//
//    }
//
//    @Test
//    public void settingSubjectShouldWork() throws Exception {
//
//        // todo: make sure to cover the full range of cases, e.g., revising then interrupted then returned
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("gc1", testUser);
//        MeetingRequest testMeeting = MeetingRequest.makeEmpty(testUser, testGroup);
//
//        String urlToCheck = saveMeetingMenu("subject", testMeeting.getUid(), false);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(eventRequestBrokerMock.createEmptyMeetingRequest(testUser.getUid(), testGroup.getUid())).thenReturn(testMeeting);
//        when(eventRequestBrokerMock.load(testMeeting.getUid())).thenReturn(testMeeting);
//
//        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("groupUid", "" + testGroup.getUid())
//                                .param("prior_menu", "group")).andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToCheck).param(phoneParam, testUserPhone).param("request", "1"))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(cacheUtilManagerMock, times(2)).putUssdMenuForUser(testUserPhone, urlToCheck);
//        verifyNoMoreInteractions(cacheUtilManagerMock);
//        verify(eventRequestBrokerMock, times(1)).createEmptyMeetingRequest(testUser.getUid(), testGroup.getUid());
//        verify(eventRequestBrokerMock, times(1)).load(testMeeting.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void settingLocationShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("test", testUser);
//        MeetingRequest dummyRequest = MeetingRequest.makeEmpty(testUser, testGroup);
//        String urlToSave = saveMeetingMenu("place", dummyRequest.getUid(), false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(dummyRequest.getUid())).thenReturn(dummyRequest);
//
//        mockMvc.perform(get(path + "place").param(phoneParam, testUserPhone).param("entityUid", dummyRequest.getUid()).
//                param("prior_menu", "subject").param("request", "unit test")).andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(nullable(String.class), nullable(String.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(2)).load(dummyRequest.getUid());
//        verify(eventBrokerMock, times(2)).getMostFrequentLocation(testGroup.getUid());
//        verify(eventRequestBrokerMock, times(1)).updateName(testUser.getUid(), dummyRequest.getUid(), "unit test");
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//        verifyZeroInteractions(groupBrokerMock);
//    }
//
//    @Test
//    public void settingDateTimeShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        String requestUid = MeetingRequest.makeEmpty().getUid();
//        String urlToSave = saveMeetingMenu("time", requestUid, false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "time").param(phoneParam, testUserPhone).param("entityUid", requestUid).
//                param("prior_menu", "place").param("request", "JoziHub")).andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(nullable(String.class), nullable(String.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).updateMeetingLocation(testUser.getUid(), requestUid, "JoziHub");
//        verifyNoMoreInteractions(eventBrokerMock);
//        verifyZeroInteractions(groupBrokerMock);
//    }
//
//    // major todo: test revise and return
//
//    @Test
//    public void confirmationMenuShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("gc1", testUser);
//        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
//        String requestUid = meetingForTest.getUid();
//
//        LocalDateTime forTimestamp = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(7, 0));
//        meetingForTest.setEventStartDateTime(convertToSystemTime(forTimestamp, getSAST()));
//        String urlToSave = saveMeetingMenu("confirm", requestUid, false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(requestUid)).thenReturn(meetingForTest);
//        when(learningServiceMock.parse("Tomorrow 7am"))
//                .thenReturn(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(7,0)));
//
//        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
//                param("prior_menu", "time").param("request", "Tomorrow 7am")).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class), nullable(String.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(testUser.getUid(), requestUid, forTimestamp);
//        verify(eventRequestBrokerMock, times(1)).load(requestUid);
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//        verifyZeroInteractions(groupBrokerMock);
//
//    }
//
//    @Test
//    public void meetingChangeTimeShouldWork() throws Exception {
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("someGroup", testUser);
//        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
//        meetingForTest.setEventStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS));
//
//        String urlToSave = saveMeetingMenu("time_only", meetingForTest.getUid(), false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(meetingForTest.getUid())).thenReturn(meetingForTest);
//
//        mockMvc.perform(get(path + "time_only").param(phoneParam, testUserPhone).param("entityUid", meetingForTest.getUid()))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).load(meetingForTest.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void meetingChangeDateShouldWork() throws Exception {
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("someGroup", testUser);
//        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
//        meetingForTest.setEventStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS));
//        String urlToSave = saveMeetingMenu("date_only", meetingForTest.getUid(), false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(meetingForTest.getUid())).thenReturn(meetingForTest);
//
//        mockMvc.perform(get(path + "date_only").param(phoneParam, testUserPhone).param("entityUid", meetingForTest.getUid()))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).load(meetingForTest.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }

    @Test
    public void timeProcessingShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Group testGroup = new Group("tg1", GroupPermissionTemplate.DEFAULT_GROUP, testUser);

        LocalDateTime timestamp = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0));

        MeetingRequest meetingForTest = MeetingRequest.makeEmpty(testUser, testGroup);
        meetingForTest.setEventStartDateTime(convertToSystemTime(timestamp, getSAST()));
        String requestUid = meetingForTest.getUid();

        String urlToSave = saveMeetingMenu("confirm", requestUid, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(meetingForTest);

        List<String> nineAmVariations = Arrays.asList("09:00", "09 00", "9h00", "9:00 am", "9am");
        List<String> onePmVariations = Arrays.asList("13:00", "13 00", "13h00", "1:00 pm", "1pm");

        for (String time : nineAmVariations) {
            this.ussdMeetingService.processConfirmDetails(testUserPhone, requestUid, "time_only", time, false);
        }

        for (String time : onePmVariations) {
            this.ussdMeetingService.processConfirmDetails(testUserPhone, requestUid, "time_only", time, false);
        }

        // not doing the full range of checks as those are tested above, here just verifying no extraneous calls
        log.info("here is the timestamp at present = " + timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        verify(eventRequestBrokerMock, times(nineAmVariations.size())).updateEventDateTime(testUser.getUid(), requestUid, timestamp);
        verify(eventRequestBrokerMock, times(onePmVariations.size())).updateEventDateTime(testUser.getUid(), requestUid, timestamp);
    }

//    @Test
//    public void dateProcessingShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Group testGroup = new Group("tg1", testUser);
//        LocalDateTime forTimestamp = LocalDateTime.of(testYear.getValue(), 6, 16, 13, 0);
//        MeetingRequest testMeeting = MeetingRequest.makeEmpty(testUser, testGroup);
//        testMeeting.setEventStartDateTime(convertToSystemTime(forTimestamp, getSAST()));
//        String requestUid = testMeeting.getUid();
//
//        String urlToSave = saveMeetingMenu("confirm", requestUid, false);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        // as above, specifying string makes sure it gets formatted appropriately (keep an eye on year though)
//        when(eventRequestBrokerMock.load(requestUid)).thenReturn(testMeeting);
//
//        List<String> bloomVariations = Arrays.asList("16-06", "16 06", "16/06", "16-6", "16 6", "16/6",
//                                                     "16-06-2018", "16 06 2018", "16/06/2018", "16-6-2018", "16/6/2018");
//
//        for (String date : bloomVariations) {
//            mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", testMeeting.getUid()).
//                    param("prior_menu", "date_only").param("revising", "1").param("request", date)).andExpect(status().isOk());
//        }
//
//        verify(eventRequestBrokerMock, times(bloomVariations.size())).updateEventDateTime(testUser.getUid(), requestUid, forTimestamp);
//    }

    @Test
    public void sendConfirmationScreenShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        MeetingRequest meetingRequest = MeetingRequest.makeEmpty();
        String requestUid = meetingRequest.getUid();

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        LocalDateTime forTimestamp = LocalDateTime.now().plusDays(1).plusHours(7);
        String confirmedTime = forTimestamp.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"));

        this.ussdMeetingService.processSendMessage(testUserPhone, requestUid);

        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class), nullable(String.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).finish(testUser.getUid(), requestUid, true);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyZeroInteractions(groupBrokerMock);
    }

    @Test
    public void manageMeetingMenuShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Group somegroup = new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        Event testMeeting = new MeetingBuilder().setName("someMeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(somegroup).setEventLocation("someLoc").createMeeting();

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventBrokerMock.load(testMeeting.getUid())).thenReturn(testMeeting);

        this.ussdMeetingService.processMeetingManage(testUserPhone, testMeeting.getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class));
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void viewMeetingDetailsShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Group somegroup = new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        Meeting testMeeting = new MeetingBuilder().setName("someMeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(somegroup).setEventLocation("someLoc").createMeeting();
        testMeeting.setRsvpRequired(true);

        Map<String, String> meetingDetails = new HashMap<>();
        meetingDetails.put("groupName", "Test Group");
        meetingDetails.put("location", "JoziHub");
        meetingDetails.put("dateTimeString", "Sat 23 Sep 2055, 11:11 am");

        ResponseTotalsDTO meetingResults = ResponseTotalsDTO.makeForTest(115, 54, 0, 546, 600);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventBrokerMock.loadMeeting(testMeeting.getUid())).thenReturn(testMeeting);
        when(eventLogBrokerMock.getResponseCountForEvent(testMeeting)).thenReturn(meetingResults);

        this.ussdMeetingService.processMeetingDetails(testUserPhone, testMeeting.getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventBrokerMock, times(1)).loadMeeting(testMeeting.getUid());
        verify(eventLogBrokerMock, times(1)).getResponseCountForEvent(testMeeting);
        verifyNoMoreInteractions(eventBrokerMock);
        verifyZeroInteractions(groupBrokerMock);

    }

    @Test
    public void changeDateOnlyShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser)).setEventLocation("someloc").createMeeting();
        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);
        String urlToSave = editingMtgMenuUrl("new_date", testMeeting.getUid(), changeRequest.getUid(), null);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventRequestBrokerMock.createChangeRequest(testUser.getUid(), testMeeting.getUid())).thenReturn(changeRequest);
        when(eventRequestBrokerMock.load(changeRequest.getUid())).thenReturn(changeRequest);


        this.ussdMeetingService.processNewMeetingDate(testUserPhone, testMeeting.getUid(), null);
        this.ussdMeetingService.processNewMeetingDate(testUserPhone, testMeeting.getUid(), changeRequest.getUid());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(2)).putUssdMenuForUser(testUserPhone, urlToSave);
        verifyNoMoreInteractions(cacheUtilManagerMock);
        verify(eventRequestBrokerMock, times(1)).createChangeRequest(testUser.getUid(), testMeeting.getUid());
        verify(eventRequestBrokerMock, times(1)).load(changeRequest.getUid());
        verifyNoMoreInteractions(eventRequestBrokerMock);
        verifyZeroInteractions(groupBrokerMock);

    }

//    @Test
//    public void changeTimeOnlyShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", testUser)).setEventLocation("someloc").createMeeting();
//        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);
//        String urlToSave = editingMtgMenuUrl("new_time", testMeeting.getUid(), changeRequest.getUid(), null);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(eventRequestBrokerMock.createChangeRequest(testUser.getUid(), testMeeting.getUid())).thenReturn(changeRequest);
//        when(eventRequestBrokerMock.load(changeRequest.getUid())).thenReturn(changeRequest);
//
//        mockMvc.perform(get(path + "new_time").param(phoneParam, testUserPhone).param("entityUid", "" + testMeeting.getUid()).
//                param("next_menu", "changeDateTime")).andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(cacheUtilManagerMock, times(2)).putUssdMenuForUser(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(cacheUtilManagerMock);
//        verify(eventRequestBrokerMock, times(1)).createChangeRequest(testUser.getUid(), testMeeting.getUid());
//        verify(eventRequestBrokerMock, times(1)).load(changeRequest.getUid());
//        verifyNoMoreInteractions(eventBrokerMock);
//        verifyZeroInteractions(groupBrokerMock);
//
//    }
//
//    @Test
//    public void changeDateAndTimeShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        LocalDateTime original = LocalDateTime.of(testYear.getValue(), 06, 15, 10, 0);
//        LocalDateTime changedDate = original.plusDays(1L);
//        LocalDateTime changedTime = original.minusHours(1L);
//        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(convertToSystemTime(original, getSAST())).setUser(testUser).setParent(new Group("somegroup", testUser)).setEventLocation("someloc").createMeeting();
//        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);
//
//        String dateUrlToSave = editingMtgMenuUrl("modify", testMeeting.getUid(), changeRequest.getUid(), "new_date")
//                + "&prior_input=" + encodeParameter("16-06");
//        String timeUrlToSave = editingMtgMenuUrl("modify", testMeeting.getUid(), changeRequest.getUid(), "new_time")
//                + "&prior_input=" + encodeParameter("09 00");
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(changeRequest.getUid())).thenReturn(changeRequest);
//
//        mockMvc.perform(get(path + "modify").param(phoneParam, testUserPhone).param("entityUid", "" + testMeeting.getUid()).
//                param("requestUid", changeRequest.getUid()).param("action", "new_date").param("request", "16-06")).
//                andExpect(status().isOk());
//
//        mockMvc.perform(get(path + "modify").param(phoneParam, testUserPhone).param("entityUid", "" + testMeeting.getUid()).
//                param("requestUid", changeRequest.getUid()).param("action", "new_time").param("request", "09 00")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, dateUrlToSave);
//        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, timeUrlToSave);
//        verify(eventRequestBrokerMock, times(4)).load(changeRequest.getUid());
////        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(testUser.getUid(), changeRequest.getUid(), changedDate);
//        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(testUser.getUid(), changeRequest.getUid(), changedTime);
////        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void modifyDateTimeSendShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", testUser)).setEventLocation("someloc").createMeeting();
//        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "modify-do").param(phoneParam, testUserPhone).param("entityUid", testMeeting.getUid()).
//                param("requestUid", changeRequest.getUid())).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).finishEdit(testUser.getUid(), testMeeting.getUid(), changeRequest.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void changeLocationPromptShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", testUser)).setEventLocation("JoziHub").createMeeting();
//        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);
//        String urlToSave = editingMtgMenuUrl("changeLocation", testMeeting.getUid(), changeRequest.getUid(), null);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(eventRequestBrokerMock.createChangeRequest(testUser.getUid(), testMeeting.getUid())).thenReturn(changeRequest);
//        when(eventRequestBrokerMock.load(changeRequest.getUid())).thenReturn(changeRequest);
//
//        mockMvc.perform(get(path + "changeLocation").param(phoneParam, testUserPhone).param("entityUid", testMeeting.getUid()))
//                .andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(cacheUtilManagerMock, times(2)).putUssdMenuForUser(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(cacheUtilManagerMock);
//        verify(eventRequestBrokerMock, times(1)).createChangeRequest(testUser.getUid(), testMeeting.getUid());
//        verify(eventRequestBrokerMock, times(1)).load(changeRequest.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//        verifyZeroInteractions(groupBrokerMock);
//
//    }

    @Test
    public void meetingModificationConfirmationScreenShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);

        List<String[]> actionsAndInputs = Arrays.asList(new String[]{ "error", "wrong"},
                                                        new String[]{ "changeLocation", "Braam"},
                                                        new String[]{ "new_date", "09:00"});

        String urlToSave;
        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser)).setEventLocation("someloc").createMeeting();
        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(changeRequest.getUid())).thenReturn(changeRequest);

        for (String[] actions : actionsAndInputs) {
            urlToSave = editingMtgMenuUrl("modify", testMeeting.getUid(), changeRequest.getUid(), actions[0])
                    + "&prior_input=" + encodeParameter(actions[1]);

            this.ussdMeetingService.processModifyMeeting(testUserPhone, testMeeting.getUid(), changeRequest.getUid(), actions[0], actions[1], null);

            verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, urlToSave);
        }

        verify(userManagementServiceMock, times(actionsAndInputs.size())).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(cacheUtilManagerMock);
        verify(eventRequestBrokerMock, times(actionsAndInputs.size() + 1)).load(changeRequest.getUid());
        verify(eventRequestBrokerMock, times(1)).updateMeetingLocation(testUser.getUid(), changeRequest.getUid(), "Braam");
        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(eq(testUser.getUid()), eq(changeRequest.getUid()), any(LocalDateTime.class));
        verifyNoMoreInteractions(eventRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void meetingModificationSendShouldWork() throws Exception {

        User testUser = new User(testUserPhone, null, null);
        Meeting testMeeting = new MeetingBuilder().setName("test meeeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser)).setEventLocation("JoziHub").createMeeting();
        MeetingRequest changeRequest = MeetingRequest.makeCopy(testMeeting);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        this.ussdMeetingService.processModifyMeetingSend(testUserPhone, testMeeting.getUid(), changeRequest.getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).finishEdit(testUser.getUid(), testMeeting.getUid(), changeRequest.getUid());
        verifyNoMoreInteractions(eventRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

//    @Test
//    public void cancelMeetingPromptShouldWork() throws Exception {
//
//        User testUser = new User(testUserPhone, null, null);
//        Event testMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", testUser)).setEventLocation("someloc").createMeeting();
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        mockMvc.perform(get(path + "cancel").param(phoneParam, testUserPhone).param("entityUid", testMeeting.getUid())).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(nullable(String.class));
//        // note: not verifying interaction times with other services, until have permissions / filters in place
//    }

    @Test
    public void cancelMeetingDoShouldWork() throws Exception {
        User testUser = new User(testUserPhone, null, null);
        Event testMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(Instant.now()).setUser(testUser).setParent(new Group("somegroup", GroupPermissionTemplate.DEFAULT_GROUP, testUser)).setEventLocation("someloc").createMeeting();

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        this.ussdMeetingService.processMeetingCancelConfirmed(testUserPhone, testMeeting.getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventBrokerMock, times(1)).cancel(testUser.getUid(), testMeeting.getUid(), true);
    }

}
