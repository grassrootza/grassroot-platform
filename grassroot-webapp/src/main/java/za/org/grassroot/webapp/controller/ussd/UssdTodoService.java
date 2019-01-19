package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdTodoService {
	String REL_PATH = "todo";

	USSDMenu respondToTodo(User user, Todo todo);

	Request processVolunteerResponse(String msisdn, String todoUid, String userResponse) throws URISyntaxException;

	Request processConfirmInfoResponse(String msisdn, String todoUid, String userResponse, String priorInput) throws URISyntaxException;

	Request processReviseInfoRequest(String msisdn, String todoUid, String userResponse, String priorInput) throws URISyntaxException;

	Request processRecordInfoResponse(String msisdn, String todoUid, String response) throws URISyntaxException;

	Request processStart(String msisdn) throws URISyntaxException;

	Request processCreate(String msisdn, TodoType type, String storedUid) throws URISyntaxException;

	Request processAskForSubject(String msisdn, String storedUid, String groupUid, Boolean revising) throws URISyntaxException;

	Request processAskForDeadline(String msisdn, String storedUid, String userInput, String priorInput, Boolean revising) throws URISyntaxException;

	Request processAskForResponseTag(String msisdn, String storedUid, String userInput, String priorInput, Boolean revising) throws URISyntaxException;

	Request processValidateTodoCompletion(String msisdn, String todoUid, String userResponse) throws URISyntaxException;

	Request processConfirmTodoCreation(String msisdn, String storedUid, String userInput, String priorInput, String field) throws URISyntaxException;

	Request processFinishTodoEntry(String inputNumber, String storedUid) throws URISyntaxException;

	Request processViewExistingTodos(String inputNumber, Integer page, Boolean fetchAll) throws URISyntaxException;

	Request processAlterDateConfirm(String msisdn, String todoUid, String userInput, String priorInput) throws URISyntaxException;

	Request processAlterTodoDate(String msisdn, String todoUid) throws URISyntaxException;

	Request processAlterTodoMenu(String msisdn, String todoUid) throws URISyntaxException;

	Request processAlterTodoSubject(String msisdn, String todoUid) throws URISyntaxException;

	Request processCancelTodo(String msisdn, String todoUid) throws URISyntaxException;

	Request processViewTodoEntry(String inputNumber, String todoUid) throws URISyntaxException;

	Request processEmailResponseDo(String inputNumber, String todoUid, String emailAddress) throws URISyntaxException;

	Request processMarkTodoCompleteDone(String inputNumber, String todoUid) throws URISyntaxException;

	Request processConfirmSubjectModification(String msisdn, String todoUid, String subject) throws URISyntaxException;

	Request processChangeEntryConfirmed(String msisdn, String todoUid, long epochMillis) throws URISyntaxException;

	Request processCancelTodoConfirmed(String msisdn, String todoUid, boolean confirmed) throws URISyntaxException;

	Request processEmailTodoResponses(String inputNumber, String todoUid) throws URISyntaxException;

	Request processMarkTodoCompletePrompt(String inputNumber, String todoUid) throws URISyntaxException;
}
