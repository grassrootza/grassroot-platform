package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.User;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/12/18.
 * todo : refactor these to new USSD design
 */
public class USSDTodoControllerTest extends USSDAbstractUnitTest {

    public static final String assignUserID = "assignUserUid";

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String todoUidParam = "todoUid";
    private static final String dummyUserInput = "blah blah blah blah";
    private static final String groupMenu = "group",
            subjectMenu = "subject",
            dueDateMenu = "due_date",
            confirmMenu = "confirm",
            send = "send";
    private static final String listEntriesMenu = "list",
            viewEntryMenu = "view",
            setCompleteMenu = "complete";

    private static final String path = "/ussd/todo/";

    @InjectMocks private USSDHomeController ussdHomeController;
    @InjectMocks private USSDTodoController ussdTodoController;

    private User testUser;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ussdHomeController, ussdTodoController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpHomeController(ussdHomeController);
        wireUpMessageSourceAndGroupUtil(ussdTodoController);
        ussdTodoController.setGroupUtil(ussdGroupUtil);
        testUser = new User(testUserPhone);
    }

    @Test
    public void startMenuShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        mockMvc.perform(get(path + "start")
                .param(phoneParam, testUserPhone))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    // todo : fix the below, refactoring to new design
    /* @Test
    public void groupSelectMenuShouldWorkWithGroup() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                new Group("tg2", testUser),
                new Group("tg3", testUser));
        testGroups.stream().forEach(tg -> tg.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY)).thenReturn(3);
        when(permissionBrokerMock.getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3)).thenReturn(testGroups);

        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY)).thenReturn(0);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void listEntriesMenuShouldWork() throws Exception {
        String message = "some message about meeting some other people to discuss something important about the community";
        Instant now = Instant.now();

        Group testGroup = new Group("somegroup", testUser);
        List<Todo> testTodos = Arrays.asList(
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now));

        Page<Todo> dummyPage = new PageImpl<>(testTodos);
        PageRequest pageRequest = new PageRequest(0, 3, new Sort(Sort.Direction.DESC, "actionByDate"));
        String urlToSave = todosViewGroupCompleteEntries(listEntriesMenu, testGroup.getUid(), 0);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.fetchPageOfTodosForUser(testUser.getUid(), true, false, pageRequest)).thenReturn(dummyPage);

        mockMvc.perform(get(path + listEntriesMenu).param(phoneParam, testUserPhone)
                .param("groupUid", testGroup.getUid())
                .param("pageNumber", String.valueOf(0))).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoBrokerMock, times(1)).fetchPageOfTodosForUser(testUser.getUid(), true, false, pageRequest);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void askForSubjectShouldWork() throws Exception {
        Group dummyGroup = new Group("", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, dummyGroup);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        // when(todoRequestBrokerMock.create(testUser.getUid(), dummyGroup.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, saveToDoMenu("subject", dummyLogBook.getUid()));
        verifyNoMoreInteractions(cacheUtilManagerMock);
        // verify(todoRequestBrokerMock, times(1)).create(testUser.getUid(), dummyGroup.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void askForDueDateShouldWorkAfterInterruption() throws Exception {

        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);
        String urlToSave = saveToDoMenu("due_date", dummyLogBook.getUid(), dummyUserInput);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).
                thenReturn(testUser);

        mockMvc.perform(get(path + dueDateMenu).param(todoUidParam, dummyLogBook.getUid()).param(phoneParam, testUserPhone)
                .param("prior_input", dummyUserInput).param("interrupted", String.valueOf(true))
                .param("revising", String.valueOf(false)).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        // todo : restore when build test for instant todo
        // verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), dummyUserInput);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    @Test
    public void confirmTodoWorksWhenAssignedToUser() throws Exception {

        Group testGroup = new Group("testGroup", testUser);
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        testUser.setDisplayName("Paballo");

        dummyLogBook.setMessage(dummyUserInput);
        dummyLogBook.setActionByDate(Instant.now());

        String urlToSave = saveToDoMenu(confirmMenu, dummyLogBook.getUid(), subjectMenu, "revised message", true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + confirmMenu).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("request", "revised message").param("prior_menu", subjectMenu))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verify(todoRequestBrokerMock, times(2)).load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), "revised message");
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        Group testGroup = new Group("test testGroup", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        LocalDateTime correctDueDate = LocalDateTime.of(testYear.getValue(), testDay.getMonthValue(), testDay.getDayOfMonth(), 13, 0);
        List<String> bloomVariations = Arrays.asList("%02d-%02d", "%02d %02d", "%02d/%02d", "%d-%d", "%d %d", "%d/%d",
                "%02d-%02d-%d", "%02d %02d %d", "%02d/%02d/%d", "%d-%d-%d", "%d/%d/%d");

	    for (String format : bloomVariations) {
	        String date = String.format(format, testDay.getDayOfMonth(), testDay.getMonthValue(), testYear.getValue());
		    String urlToSave = USSDUrlUtil.saveToDoMenu(confirmMenu, dummyLogBook.getUid(), dueDateMenu, date, true);
	        String formattedDateString = reformatDateInput(date).trim();

            dummyLogBook.setActionByDate(convertDateStringToLocalDateTime(formattedDateString, 13, 0).toInstant(ZoneOffset.UTC));
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

            mockMvc.perform(get(path + confirmMenu).param(phoneParam, testUserPhone).param(todoUidParam, dummyLogBook.getUid())
                    .param("prior_input", date).param("prior_menu", "due_date").param("request", "1"))
                    .andExpect(status().isOk());
        }

        verify(userManagementServiceMock, times(bloomVariations.size()))
                .findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(bloomVariations.size()))
                .load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(bloomVariations.size()))
                .updateDueDate(testUser.getUid(), dummyLogBook.getUid(), correctDueDate);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    @Test
    public void finishTodoShouldWork() throws Exception {
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + send).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(1)).finish(dummyLogBook.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void viewEntryMenuWorks() throws Exception {
        Group testGroup = new Group("test testGroup", testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "Some todo subject", Instant.now());
        dummyTodo.getAncestorGroup().addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()))).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewEntryMenu).param(todoUidParam, dummyTodo.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()));
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void todoCompleteMenuWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "test todo", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));

        String urlToSave = saveToDoMenu(setCompleteMenu, dummyTodo.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + setCompleteMenu).param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoBrokerMock, times(2)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void setActionTodoComplete() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "test logbook", Instant.now().plus(1, ChronoUnit.DAYS));
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));
        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + setCompleteMenu + "-do")
                .param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())
                .param("prior_input", TodoCompletionConfirmType.COMPLETED.name())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);

        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verify(todoBrokerMock, times(1)).recordValidation(eq(testUser.getUid()), eq(dummyTodo.getUid()), eq(TodoCompletionConfirmType.COMPLETED),
                any(LocalDateTime.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }*/

}
