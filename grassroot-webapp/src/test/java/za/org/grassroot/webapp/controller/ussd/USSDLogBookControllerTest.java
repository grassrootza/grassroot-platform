package za.org.grassroot.webapp.controller.ussd;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupPage;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.convertDateStringToLocalDateTime;
import static za.org.grassroot.core.util.DateTimeUtil.reformatDateInput;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/12/18.
 */
public class USSDLogBookControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDLogBookControllerTest.class);

    public static final String assignUserID = "assignUserUid";

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String logBookIdParam = "logbookUid";
    private static final Long dummyId = 1L;
    private static final String dummyUserInput = "blah blah blah blah";
    private static final int hour = 13;
    private static final int minutes = 00;
    private static final String groupMenu = "group",
            subjectMenu = "subject",
            dueDateMenu = "due_date",
            assignMenu = "assign",
            searchUserMenu = "search_user",
            pickUserMenu = "pick_user",
            confirmMenu = "confirm",
            send = "send";
    private static final String entryTypeMenu = "type",
            listEntriesMenu = "list",
            viewEntryMenu = "view",
            viewEntryDates = "view_dates",
            viewAssignment = "view_assigned",
            setCompleteMenu = "set_complete",
            viewCompleteMenu = "view_complete",
            completingUser = "choose_completor",
            pickCompletor = "pick_completor",
            completedDate = "date_completed",
            confirmCompleteDate = "confirm_date",
            confirmComplete = "confirm_complete";

    private static final String path = "/ussd/log/";

    @InjectMocks
    USSDLogBookController ussdLogBookController;

    @Mock
    UserRepository userRepository;


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
        for (Group testGroup : testGroups) {
            testGroup.addMember(testUser);
        }
        GroupPage pageOfGroups = GroupPage.createFromGroups(testGroups, 0, 3);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(true);
        when(permissionBrokerMock.getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3)).thenReturn(pageOfGroups);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).isPartOfActiveGroups(testUser);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(false);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).isPartOfActiveGroups(testUser);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void listEntriesMenuShouldWork() throws Exception {

        String message = "some message about meeting some other people to" +
                " discuss something important about the community";
        Instant now = Instant.now();
        Group testGroup = new Group("somegroup", testUser);
        List<LogBook> testLogBooks = Arrays.asList(
                new LogBook(testUser, testGroup, message, now),
                new LogBook(testUser, testGroup, message, now),
                new LogBook(testUser, testGroup, message, now));

        Page<LogBook> dummyPage = new PageImpl<>(testLogBooks);
        String urlToSave = logViewExistingUrl(listEntriesMenu, testGroup.getUid(), true, 0);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookBrokerMock.retrieveGroupLogBooks(testUser.getUid(), testGroup.getUid(), true, 0, 3)).thenReturn(dummyPage);
        mockMvc.perform(get(path + listEntriesMenu).param(phoneParam, testUserPhone).param("groupUid", testGroup.getUid())
                .param("done", String.valueOf(true))).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(logBookBrokerMock, times(1)).retrieveGroupLogBooks(testUser.getUid(), testGroup.getUid(), true, 0, 3);
        verifyNoMoreInteractions(logBookBrokerMock);
    }

    @Test
    public void askForSubjectShouldWork() throws Exception {
        testUser.setId(dummyId);
        Group dummyGroup = new Group("", testUser);
        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser, dummyGroup);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(logBookRequestBrokerMock.create(testUser.getUid(), dummyGroup.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, saveLogMenu("subject", dummyLogBook.getUid()));
        verifyNoMoreInteractions(cacheUtilManagerMock);
        verify(logBookRequestBrokerMock, times(1)).create(testUser.getUid(), dummyGroup.getUid());
        verifyNoMoreInteractions(logBookRequestBrokerMock);
    }

    @Test
    public void askForDueDateShouldWorkAfterInterruption() throws Exception {

        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser);
        String urlToSave = saveLogMenu("due_date", dummyLogBook.getUid(), dummyUserInput);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).
                thenReturn(testUser);

        mockMvc.perform(get(path + dueDateMenu).param(logBookIdParam, dummyLogBook.getUid()).param(phoneParam, testUserPhone)
                .param("prior_input", dummyUserInput).param("interrupted", String.valueOf(true))
                .param("revising", String.valueOf(false)).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(logBookRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), dummyUserInput);
        verifyNoMoreInteractions(logBookRequestBrokerMock);

    }

    /* Since we are no longer doing user assignment through USSD, this is redundant

    @Test
    public void askForAssignmentWorksAfterInterruption() throws Exception {

        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser);

        String priorInput = "20/1";

        String urlToSave = USSDUrlUtil.saveLogMenu(assignMenu, dummyLogBook.getUid(), priorInput);
        String formattedDateString = reformatDateInput(priorInput).trim();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + assignMenu).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("prior_input", priorInput).param("interrupted", String.valueOf(true)).param("request", "1").param("revising", String.valueOf(false)))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookRequestBrokerMock, times(1)).updateDueDate(testUser.getUid(), dummyLogBook.getUid(),
                                                                 parsePreformattedDate(reformatDateInput(priorInput).trim(), hour, minutes));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);

    }*/

    /* As above, commenting out -- possibly to delete later
    @Test
    public void searchUserWorksAfterInterruption() throws Exception {

        LogBook dummyLogBook = LogBook.makeEmpty();
        String urlToSave = USSDUrlUtil.saveLogMenu(searchUserMenu, dummyLogBook.getUid());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + searchUserMenu).param(logBookIdParam, String.valueOf(dummyId))
                .param(phoneParam, testUserPhone).param("prior_input", "1").param("interrupted", String.valueOf(true))
                .param("request", "1"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
    }
    */

    /* As above, leaving this out
    @Test
    public void askForAssignedUsersWorksWithNumberAfterInterruption() throws Exception {

        LogBook dummyLogBook = LogBook.makeEmpty();
        dummyLogBook.setId(dummyId);
        dummyLogBook.setParent(new Group("somegroup", testUser));
        String urlToSave = USSDUrlUtil.saveLogMenu(pickUserMenu, dummyLogBook.getUid(), testUserPhone);
        List<User> dummyPossibleUsers = Arrays.asList(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookServiceMock.load(dummyId)).thenReturn(dummyLogBook);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyLogBook.resolveGroup().getUid(), testUserPhone))
                .thenReturn(dummyPossibleUsers);
        mockMvc.perform(get(path + pickUserMenu).param(logBookIdParam,
                String.valueOf(dummyId)).param("prior_input", testUserPhone).param(phoneParam, testUserPhone)
                .param("interrupted", String.valueOf(true)).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookServiceMock, times(1)).load(dummyId);
        verify(userManagementServiceMock, times(1)).searchByGroupAndNameNumber(dummyLogBook.resolveGroup().getUid(), testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookServiceMock);
    }*/

    @Test
    public void confirmLogEntryBookWorksWhenAssignedToUser() throws Exception {

        Group testGroup = new Group("testGroup", testUser);
        testGroup.addMember(testUser);
        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser, testGroup);

        testUser.setDisplayName("Paballo");

        dummyLogBook.setMessage(dummyUserInput);
        dummyLogBook.setActionByDate(Instant.now());

        String urlToSave = saveLogMenu(confirmMenu, dummyLogBook.getUid(), subjectMenu, "revised message", true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + confirmMenu).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("request", "revised message").param("prior_menu", subjectMenu))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verify(logBookRequestBrokerMock, times(2)).load(dummyLogBook.getUid());
        verify(logBookRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), "revised message");
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        Group testGroup = new Group("test testGroup", testUser);
        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser, testGroup);

        LocalDateTime correctDueDate = LocalDateTime.of(2016, 6, 16, 13, 0);
        List<String> bloomVariations = Arrays.asList("16-06", "16 06", "16/06", "16-6", "16 6", "16/6",
                "16-06-2016", "16 06 2016", "16/06/2016", "16-6-2016", "16/6/2016");

        for (String date : bloomVariations) {

            String urlToSave = USSDUrlUtil.saveLogMenu(confirmMenu, dummyLogBook.getUid(), dueDateMenu, date, true);
            String formattedDateString = reformatDateInput(date).trim();
            dummyLogBook.setActionByDate(convertDateStringToLocalDateTime(formattedDateString, 13, 0).toInstant(ZoneOffset.UTC));
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            when(logBookRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

            mockMvc.perform(get(path + confirmMenu).param(phoneParam, testUserPhone).param(logBookIdParam, dummyLogBook.getUid())
                    .param("prior_input", date).param("prior_menu", "due_date").param("request", "1"))
                    .andExpect(status().isOk());
        }

        verify(userManagementServiceMock, times(bloomVariations.size())).findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(logBookRequestBrokerMock, times(bloomVariations.size())).load(dummyLogBook.getUid());
        verify(logBookRequestBrokerMock, times(bloomVariations.size())).updateDueDate(testUser.getUid(), dummyLogBook.getUid(),
                                                                                      correctDueDate);
        verifyNoMoreInteractions(logBookRequestBrokerMock);

    }

    @Test
    public void finishLogBookShouldWork() throws Exception {

        LogBookRequest dummyLogBook = LogBookRequest.makeEmpty(testUser);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + send).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(logBookRequestBrokerMock, times(1)).finish(dummyLogBook.getUid());
        verifyNoMoreInteractions(logBookRequestBrokerMock);
    }

    @Test
    public void viewEntryMenuWorks() throws Exception {

        LogBook dummyLogBook = LogBook.makeEmpty();
        dummyLogBook.setCompleted(true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveLogMenu(viewEntryMenu, dummyLogBook.getUid()))).thenReturn(testUser);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + viewEntryMenu).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveLogMenu(viewEntryMenu, dummyLogBook.getUid()));
        verify(logBookBrokerMock, times(1)).load(dummyLogBook.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);
    }

    @Test
    public void viewLogBookDatesWorksWhenActionInComplete() throws Exception {

        Group testGroup = new Group("tg1", testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().plus(7, ChronoUnit.DAYS));
        dummyLogBook.setCompleted(false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + viewEntryDates).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookBrokerMock, times(1)).load(dummyLogBook.getUid());

        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void viewLogBookDatesWorksWhenActionCompleted() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));
        dummyLogBook.setCompleted(true);
        dummyLogBook.setCompletedDate(Instant.now());
        dummyLogBook.setCompletedByUser(testUser);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + viewEntryDates).param(logBookIdParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookBrokerMock, times(1)).load(dummyLogBook.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void viewLogBookAssignmentWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyLogBook.setCompleted(true);
        dummyLogBook.setCompletedDate(Instant.now());

        dummyLogBook.setCompletedByUser(testUser);
        dummyLogBook.assignMembers(Collections.singleton(testUser.getUid()));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + viewAssignment).param(phoneParam, testUserPhone)
                .param(logBookIdParam, dummyLogBook.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookBrokerMock, times(1)).load(dummyLogBook.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void logBookCompleteMenuWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyLogBook.setCompleted(true);
        dummyLogBook.setCompletedDate(Instant.now());
        dummyLogBook.setCompletedByUser(testUser);
        dummyLogBook.assignMembers(Collections.singleton(testUser.getUid()));

        String urlToSave = saveLogMenu(setCompleteMenu, dummyLogBook.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + setCompleteMenu).param(phoneParam, testUserPhone)
                .param(logBookIdParam, dummyLogBook.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void selectCompletingUserPromptWorks() throws Exception {

        LogBook dummyLogBook = LogBook.makeEmpty();
        String urlToSave = saveLogMenu(completingUser, dummyLogBook.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + completingUser).param(phoneParam, testUserPhone)
                .param(logBookIdParam, dummyLogBook.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone,
                saveLogMenu(completingUser, dummyLogBook.getUid()));
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void pickCompletorWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyLogBook.assignMembers(Collections.singleton(testUser.getUid()));

        List<User> testPossibleUsers = Arrays.asList(testUser);
        String urlToSave = saveLogMenu(pickCompletor, dummyLogBook.getUid(), testUserPhone);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.searchByGroupAndNameNumber(dummyLogBook.resolveGroup().getUid(), testUserPhone))
                .thenReturn(testPossibleUsers);
        when(logBookBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + pickCompletor).param(phoneParam, testUserPhone).param(logBookIdParam, dummyLogBook.getUid())
                                .param("request", testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone,
                saveLogMenu(pickCompletor, dummyLogBook.getUid(), testUserPhone));
        verify(userManagementServiceMock, times(2)).searchByGroupAndNameNumber(dummyLogBook.resolveGroup().getUid(), testUserPhone);
        verify(logBookBrokerMock, times(2)).load(dummyLogBook.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void completedDatePromptWorks() throws Exception {

        String logBookUid = LogBook.makeEmpty().getUid();
        String urlToSave = saveLogMenu(completedDate, logBookUid);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + completedDate).param(phoneParam, testUserPhone).param(logBookIdParam, logBookUid))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void confirmCompletedDateWorks() throws Exception {

        String logBookUid = LogBook.makeEmpty().getUid();

        String priorInput = "20 01";
        String urlToSave = saveLogMenu(confirmCompleteDate, logBookUid, priorInput);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + confirmCompleteDate).param(phoneParam, testUserPhone).param(logBookIdParam, logBookUid).
                param(userInputParam, priorInput)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userInputParam, "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

    @Test
    public void setLogBookEntryCompleteWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser);
        LogBook dummyLogBook = new LogBook(testUser, testGroup, "test logbook", Instant.now().plus(1, ChronoUnit.DAYS));

        dummyLogBook.assignMembers(Collections.singleton(testUser.getUid()));
        dummyLogBook.setCompletedByUser(testUser);
        String completed_date = "20/11";
        LocalDateTime correctDateTime = LocalDateTime.of(2016, 11, 20, 13, 0);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + setCompleteMenu + "-do").param(phoneParam, testUserPhone).
                param(logBookIdParam, dummyLogBook.getUid()).param("completed_date", completed_date).
                param(assignUserID, testUser.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(logBookBrokerMock, times(1)).complete(testUser.getUid(), dummyLogBook.getUid(), correctDateTime, testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(logBookBrokerMock);

    }

}
