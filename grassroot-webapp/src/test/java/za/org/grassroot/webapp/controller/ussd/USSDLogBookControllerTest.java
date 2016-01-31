package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveLogMenu;
import static za.org.grassroot.webapp.util.USSDUrlUtil.userInputParam;

/**
 * Created by luke on 2015/12/18.
 */
public class USSDLogBookControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDLogBookControllerTest.class);

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String logBookIdParam = "logbookid";
    public static final String assignUserID = "assignUserId";

    private static final Long testLogBookId = 1L;
    private static final Long dummyId = 1L;
    private static final String dummyUserInput = "blah blah blah blah";
    private static final int hour = 13;
    private static final int minutes = 00;
    private static final String groupMenu = "group", subjectMenu = "subject", dueDateMenu = "due_date",
            assignMenu = "assign", searchUserMenu = "search_user", pickUserMenu = "pick_user", confirmMenu = "confirm", send = "send";
    private static final String listEntriesMenu = "list", viewEntryMenu = "view", viewEntryDates = "view_dates",
            viewAssignment = "view_assigned", setCompleteMenu = "set_complete", viewCompleteMenu = "view_complete",
            completingUser = "choose_completor", pickCompletor = "pick_completor", completedDate = "date_completed",
            confirmCompleteDate = "confirm_date", confirmComplete = "confirm_complete";

    private static final String path = "/ussd/log/";

    @InjectMocks
    private USSDLogBookController ussdLogBookController;

    @Mock
    private UserRepository userRepository;

    private User testUser;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdLogBookController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdLogBookController, ussdGroupUtil);
        testUser = new User(testUserPhone);
        testUser.setId(dummyId);

    }

    @Test
    public void groupSelectMenuShouldWorkWithGroup() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                new Group("tg2", testUser),
                new Group("tg3", testUser));
        testUser.setGroupsPartOf(testGroups);
        Page<Group> pageOfGroups = new PageImpl<>(testGroups);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(pageOfGroups);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(testUser, 0, 3);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(false);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void listEntriesMenuShouldWork() throws Exception {

        String message = "some message about meeting some other people to" +
                " discuss something important about the community";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<LogBook> testLogBooks = Arrays.asList(new LogBook(dummyId, message, now),
                new LogBook(2L, message, now), new LogBook(3L, message, now), new LogBook(4L, message, now),
                new LogBook(5L, message, now));
        Page<LogBook> dummyPageOfGroups = new PageImpl<>(testLogBooks);

        String urlToSave = USSDUrlUtil.logViewExistingUrl(listEntriesMenu,dummyId,true,0);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.getAllLogBookEntriesForGroup(dummyId, 0, 3, true)).thenReturn(dummyPageOfGroups);
        mockMvc.perform(get(path + listEntriesMenu).param(phoneParam, testUserPhone).param("groupId", String.valueOf(dummyId))
                .param("done", String.valueOf(true))).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).getAllLogBookEntriesForGroup(dummyId, 0, 3, true);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void askForSubjectShouldWork() throws Exception {

        testUser.setId(dummyId);
        Group dummyGroup = new Group("", testUser);
        dummyGroup.setId(dummyId);
        LogBook dummyLogBook = new LogBook();

        dummyLogBook.setId(dummyId);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(logBookServiceMock.create(testUser.getId(), dummyGroup.getId(), false)).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + "subject").param("msisdn", testUserPhone).param("groupId", String.valueOf(dummyId)))

                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, saveLogMenu("subject", dummyId));
        verify(logBookServiceMock, times(1)).create(testUser.getId(), dummyGroup.getId(), false);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void askForDueDateShouldWorkAfterInterruption() throws Exception {

        LogBook dummyLogBook = new LogBook();
        String urlToSave = saveLogMenu("due_date", dummyId, dummyUserInput);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).
                thenReturn(testUser);
        when(logBookServiceMock.setMessage(dummyId, dummyUserInput)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path + dueDateMenu).param(logBookIdParam, String.valueOf(dummyId)).param(phoneParam, testUserPhone)
                .param("prior_input", dummyUserInput).param("interrupted", String.valueOf(true))
                .param("revising", String.valueOf(false)).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).setMessage(dummyId, dummyUserInput);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void askForAssignmentWorksAfterInterruption() throws Exception {

        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);

        String priorInput = "20/1";

        String urlToSave= saveLogMenu(assignMenu,dummyId,priorInput);
        String formattedDateString = DateTimeUtil.reformatDateInput(priorInput).trim();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.setDueDate(dummyLogBook.getId(), DateTimeUtil.parsePreformattedDate(
                formattedDateString, hour, minutes)))
                .thenReturn(dummyLogBook);
        mockMvc.perform(get(path + assignMenu).param(logBookIdParam, String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone).param("prior_input", priorInput).param("interrupted",
                        String.valueOf(true)).param("request", "1").param("revising", String.valueOf(false)))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).setDueDate(dummyLogBook.getId(), DateTimeUtil.parsePreformattedDate(
                DateTimeUtil.reformatDateInput(priorInput).trim(), hour, minutes));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }

    @Test
    public void searchUserWorksAfterInterruption() throws Exception {

        String urlToSave = saveLogMenu(searchUserMenu,dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + searchUserMenu).param(logBookIdParam, String.valueOf(dummyId))
                .param(phoneParam, testUserPhone).param("prior_input", "1").param("interrupted", String.valueOf(true))
                .param("request", "1"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void askForAssignedUsersWorksWithNumberAfterInterruption() throws Exception {

        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setGroupId(dummyId);
        String urlToSave = saveLogMenu(pickUserMenu,dummyId,testUserPhone);
        List<User> dummyPossibleUsers = Arrays.asList(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyId, testUserPhone))
                .thenReturn(dummyPossibleUsers);
        mockMvc.perform(get(path + pickUserMenu).param(logBookIdParam,
                String.valueOf(dummyId)).param("prior_input", testUserPhone).param(phoneParam, testUserPhone)
                .param("interrupted", String.valueOf(true)).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).load(dummyId);
        verify(userManagementServiceMock, times(1)).searchByGroupAndNameNumber(dummyId, testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void confirmLogEntryBookWorksWhenAssignedToUser() throws Exception {

        LogBook dummyLogBook = new LogBook();
        Group testGroup = new Group("", testUser);
        testGroup.setGroupName("testGroup");
        testGroup.setId(dummyId);

        testUser.setDisplayName("Paballo");

        dummyLogBook.setId(dummyId);
        dummyLogBook.setGroupId(dummyId);
        dummyLogBook.setMessage(dummyUserInput);
        dummyLogBook.setActionByDate(Timestamp.from(Instant.now()));
        dummyLogBook.setAssignedToUserId(testUser.getId());

        String urlToSave = saveLogMenu(confirmMenu,dummyId,"1",testUser.getId());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.setAssignedToUser(dummyLogBook.getId(), testUser.getId())).thenReturn(dummyLogBook);
        when(logBookServiceMock.load(dummyLogBook.getId())).thenReturn(dummyLogBook);
        when(userManagementServiceMock.getDisplayName(testUser.getId())).thenReturn(testUser.getDisplayName());
        when(groupManagementServiceMock.getGroupName(dummyLogBook.getGroupId())).thenReturn(testGroup.getGroupName());
        mockMvc.perform(get(path + confirmMenu).param(logBookIdParam, String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone).param("assignUserId", String.valueOf(testUser.getId()))
                .param("request", "1"));
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).setAssignedToUser(dummyLogBook.getId(), testUser.getId());
        verify(logBookServiceMock, times(1)).load(dummyLogBook.getId());
        verify(userManagementServiceMock, times(1)).getDisplayName(testUser.getId());
        verify(groupManagementServiceMock, times(1)).getGroupName(dummyLogBook.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        List<String> bloomVariations = Arrays.asList("16-06", "16 06", "16/06", "16-6", "16 6", "16/6",
                "16-06-2016", "16 06 2016", "16/06/2016", "16-6-2016", "16/6/2016");
        for (String date : bloomVariations) {
            String urlToSave = USSDUrlUtil.saveLogMenu(assignMenu,dummyId,date);
            String formattedDateString = DateTimeUtil.reformatDateInput(date).trim();
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            when(logBookServiceMock.setDueDate(dummyLogBook.getId(), DateTimeUtil.parsePreformattedDate(
                    formattedDateString, hour, minutes)))
                    .thenReturn(dummyLogBook);
            mockMvc.perform(get(path + assignMenu).param(logBookIdParam, String.valueOf(dummyLogBook.getId()))
                    .param(phoneParam, testUserPhone).param("prior_input", date).param("interrupted",
                            String.valueOf(true)).param("request", "1").param("revising", String.valueOf(false)))
                    .andExpect(status().isOk());
        }

    }
    @Test
    public void finishLogBookShouldWork() throws Exception {


        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookServiceMock.setRecorded(dummyLogBook.getId(), true)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path + send).param(logBookIdParam, String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookServiceMock, times(1)).setRecorded(dummyLogBook.getId(), true);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void viewEntryMenuWorks() throws Exception{

        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(true);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,saveLogMenu(viewEntryMenu,dummyId))).thenReturn(testUser);
        when(logBookServiceMock.load(dummyLogBook.getId())).thenReturn(dummyLogBook);
        mockMvc.perform(get(path+viewEntryMenu).param(logBookIdParam,String.valueOf(dummyLogBook.getId()))
                .param(phoneParam,testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,saveLogMenu(viewEntryMenu,dummyId));
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }
    @Test
    public void viewLogBookDatesWorksWhenActionInComplete() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(false);
        dummyLogBook.setCreatedDateTime(Timestamp.from(Instant.now()));
        dummyLogBook.setActionByDate(Timestamp.valueOf(LocalDateTime.of(2016, Month.JANUARY,20,12,0)));
        dummyLogBook.setCompletedByUserId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
       // when(userManagementServiceMock.loadUser(dummyId)).thenReturn(testUser);
        mockMvc.perform(get(path+viewEntryDates).param(logBookIdParam,String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
       // verify(userManagementServiceMock,times(1)).loadUser(dummyId);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }
    @Test
    public void viewLogBookDatesWorksWhenActionCompleted() throws Exception{
       /* LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(true);
        dummyLogBook.setCreatedDateTime(Timestamp.from(Instant.now()));
        dummyLogBook.setCompletedDate(Timestamp.from(Instant.now()));
        dummyLogBook.setActionByDate(Timestamp.valueOf(LocalDateTime.of(2016, Month.JANUARY,20,12,0)));
        dummyLogBook.setCompletedByUserId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
         when(userManagementServiceMock.loadUser(dummyId)).thenReturn(testUser);
        mockMvc.perform(get(path+viewEntryDates).param(logBookIdParam,String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
         verify(userManagementServiceMock,times(1)).loadUser(dummyId);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);*/


        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookServiceMock.setRecorded(dummyLogBook.getId(), true)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path + send).param(logBookIdParam, String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookServiceMock, times(1)).setRecorded(dummyLogBook.getId(), true);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test

    public void viewLogBookDatesWhenActionNotComplete() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(false);
        dummyLogBook.setCreatedDateTime(Timestamp.from(Instant.now()));
        dummyLogBook.setActionByDate(Timestamp.valueOf(LocalDateTime.of(2016, Month.JANUARY,20,12,0)));
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path+viewEntryDates).param(logBookIdParam,String.valueOf(dummyLogBook.getId()))
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }

    @Test
    public void viewLogBookAssignmentWorks() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(true);
        dummyLogBook.setCreatedDateTime(Timestamp.from(Instant.now()));
        dummyLogBook.setCompletedDate(Timestamp.from(Instant.now()));
        dummyLogBook.setActionByDate(Timestamp.valueOf(LocalDateTime.of(2016, Month.JANUARY,20,12,0)));
        dummyLogBook.setCompletedByUserId(dummyId);
        dummyLogBook.setAssignedToUserId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        when(userManagementServiceMock.loadUser(dummyLogBook.getAssignedToUserId())).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path+viewAssignment).param(phoneParam,testUserPhone)
                .param(logBookIdParam,String.valueOf(dummyLogBook.getId()))).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
        verify(userManagementServiceMock,times(1)).loadUser(dummyLogBook.getAssignedToUserId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }
    @Test
    public void setLogBookEntryCompleteWorks() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompleted(true);
        dummyLogBook.setCreatedDateTime(Timestamp.from(Instant.now()));
        dummyLogBook.setCompletedDate(Timestamp.from(Instant.now()));
        dummyLogBook.setActionByDate(Timestamp.valueOf(LocalDateTime.of(2016, Month.JANUARY,20,12,0)));
        dummyLogBook.setCompletedByUserId(dummyId);
        dummyLogBook.setAssignedToUserId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,saveLogMenu(setCompleteMenu,dummyLogBook.getId()))).thenReturn(testUser);
        when(userManagementServiceMock.loadUser(dummyLogBook.getAssignedToUserId())).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path+setCompleteMenu).param(phoneParam,testUserPhone)
                .param(logBookIdParam,String.valueOf(dummyLogBook.getId()))).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveLogMenu(setCompleteMenu,dummyLogBook.getId()));
        verify(userManagementServiceMock,times(1)).loadUser(dummyLogBook.getAssignedToUserId());
        verify(logBookServiceMock,times(1)).load(dummyId);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }
    @Test
    public void selectCompletingUserWorks() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,saveLogMenu(completingUser
                ,dummyLogBook.getId()))).thenReturn(testUser);
        mockMvc.perform(get(path+completingUser).param(phoneParam,testUserPhone)
                .param(logBookIdParam,String.valueOf(dummyLogBook.getId()))).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveLogMenu(completingUser,dummyLogBook.getId()));
        verifyNoMoreInteractions(userManagementServiceMock);
    }
    @Test
    public void pickCompletorWorks() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setGroupId(dummyId);
        List<User> testPossibleUsers = Arrays.asList(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,saveLogMenu(pickCompletor,dummyId,testUserPhone)))
                .thenReturn(testUser);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyLogBook.getGroupId(),testUserPhone))
                .thenReturn(testPossibleUsers);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        mockMvc.perform(get(path+pickCompletor).param(phoneParam,testUserPhone)
                .param(logBookIdParam,String.valueOf(dummyId)).param("request",testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveLogMenu(pickCompletor,dummyId,testUserPhone));
        verify(userManagementServiceMock,times(1)).searchByGroupAndNameNumber(dummyLogBook.getGroupId(),testUserPhone);
        verify(logBookServiceMock,times(1)).load(dummyLogBook.getId());
        verifyNoMoreInteractions(userManagementServiceMock);

    }
    @Test
    public void enterCompletedDateWorks() throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,saveLogMenu(completedDate,dummyId)))
                .thenReturn(testUser);
        mockMvc.perform(get(path+completedDate).param(phoneParam,testUserPhone).
                param(logBookIdParam,String.valueOf(dummyId))).andExpect(status().isOk());

    }
    @Test
    public void confirmCompletedDateWorks() throws Exception{

        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        String priorInput = "20 01";
        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveLogMenu(confirmCompleteDate,dummyId,
                priorInput))).thenReturn(testUser);
        mockMvc.perform(get(path+confirmCompleteDate).param(phoneParam,testUserPhone)
                .param(logBookIdParam,String.valueOf(dummyId)).param(userInputParam,priorInput))
                .andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveLogMenu(confirmCompleteDate,dummyId,priorInput));
        verifyNoMoreInteractions(userManagementServiceMock);

    }
    @Test
    public void setLogBookEntryDoneWorks() throws Exception{
        LogBook dummyLogBook = new LogBook();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setCompletedByUserId(2L);
        String completed_date = "20/2";
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookServiceMock.setCompleted(dummyId, 2L, DateTimeUtil.reformatDateInput(completed_date))).
                thenReturn(dummyLogBook);
        mockMvc.perform(get(path+setCompleteMenu+"-do").param(phoneParam,testUserPhone).param(logBookIdParam,
                String.valueOf(dummyLogBook.getId())).param("completed_date",completed_date).param(assignUserID,
                String.valueOf(2L))).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
        verify(logBookServiceMock,times(1)).setCompleted(dummyId,2L,DateTimeUtil.reformatDateInput(completed_date));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }

}
