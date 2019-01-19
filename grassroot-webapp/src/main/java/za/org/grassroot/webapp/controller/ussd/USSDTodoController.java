package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

@Slf4j
@RestController
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDTodoController {

	private static final String FULL_PATH = homePath + UssdTodoService.REL_PATH;

	private final UssdTodoService ussdTodoService;

	public USSDTodoController(UssdTodoService ussdTodoService) {
		this.ussdTodoService = ussdTodoService;
	}

	@RequestMapping(value = FULL_PATH + "/respond/volunteer", method = RequestMethod.GET)
	public Request volunteerResponse(@RequestParam(value = phoneNumber) String msisdn,
									 @RequestParam String todoUid,
									 @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
		return ussdTodoService.processVolunteerResponse(msisdn, todoUid, userResponse);
	}

	@RequestMapping(value = FULL_PATH + "/respond/info", method = RequestMethod.GET)
	public Request confirmInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
									   @RequestParam String todoUid,
									   @RequestParam(value = userInputParam) String userResponse,
									   @RequestParam(required = false) String priorInput) throws URISyntaxException {
		return ussdTodoService.processConfirmInfoResponse(msisdn, todoUid, userResponse, priorInput);
	}

	@RequestMapping(value = FULL_PATH + "/respond/info/confirmed", method = RequestMethod.GET)
	public Request recordInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
									  @RequestParam String todoUid,
									  @RequestParam String response) throws URISyntaxException {
		return ussdTodoService.processRecordInfoResponse(msisdn, todoUid, response);
	}

	@RequestMapping(value = FULL_PATH + "/respond/info/revise", method = RequestMethod.GET)
	public Request reviseInfoRequest(@RequestParam(value = phoneNumber) String msisdn,
									 @RequestParam String todoUid,
									 @RequestParam(value = userInputParam) String userResponse,
									 @RequestParam(required = false) String priorInput) throws URISyntaxException {
		return ussdTodoService.processReviseInfoRequest(msisdn, todoUid, userResponse, priorInput);
	}

	// note : ask when it was done?
	@RequestMapping(value = FULL_PATH + "/respond/validate", method = RequestMethod.GET)
	public Request validateTodoCompletion(@RequestParam(value = phoneNumber) String msisdn,
										  @RequestParam String todoUid,
										  @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
		return ussdTodoService.processValidateTodoCompletion(msisdn, todoUid, userResponse);
	}

    /*
    Creating todos - start with the open menu, with option to view old
     */

	@RequestMapping(value = FULL_PATH + "/start", method = RequestMethod.GET)
	public Request start(@RequestParam(value = phoneNumber) String msisdn) throws URISyntaxException {
		return ussdTodoService.processStart(msisdn);
	}

	@RequestMapping(value = FULL_PATH + "/create", method = RequestMethod.GET)
	public Request create(@RequestParam(value = phoneNumber) String msisdn,
						  @RequestParam TodoType type,
						  @RequestParam(required = false) String storedUid) throws URISyntaxException {
		return ussdTodoService.processCreate(msisdn, type, storedUid);
	}

	@RequestMapping(value = FULL_PATH + "/create/subject", method = RequestMethod.GET)
	public Request askForSubject(@RequestParam(value = phoneNumber) String msisdn,
								 @RequestParam String storedUid,
								 @RequestParam(required = false) String groupUid,
								 @RequestParam(required = false) Boolean revising) throws URISyntaxException {
		return ussdTodoService.processAskForSubject(msisdn, storedUid, groupUid, revising);
	}

	@RequestMapping(value = FULL_PATH + "/create/deadline", method = RequestMethod.GET)
	public Request askForDeadline(@RequestParam(value = phoneNumber) String msisdn,
								  @RequestParam String storedUid,
								  @RequestParam(value = userInputParam) String userInput,
								  @RequestParam(required = false) String priorInput,
								  @RequestParam(required = false) Boolean revising) throws URISyntaxException {
		return ussdTodoService.processAskForDeadline(msisdn, storedUid, userInput, priorInput, revising);
	}

	@RequestMapping(value = FULL_PATH + "/create/tag", method = RequestMethod.GET)
	public Request askForResponseTag(@RequestParam(value = phoneNumber) String msisdn,
									 @RequestParam String storedUid,
									 @RequestParam(value = userInputParam) String userInput,
									 @RequestParam(required = false) String priorInput,
									 @RequestParam(required = false) Boolean revising) throws URISyntaxException {
		return ussdTodoService.processAskForResponseTag(msisdn, storedUid, userInput, priorInput, revising);
	}

	@RequestMapping(value = FULL_PATH + "/create/confirm", method = RequestMethod.GET)
	public Request confirmTodoCreation(@RequestParam(value = phoneNumber) String msisdn,
									   @RequestParam String storedUid,
									   @RequestParam(value = userInputParam) String userInput,
									   @RequestParam(required = false) String priorInput,
									   @RequestParam(required = false) String field) throws URISyntaxException {
		return ussdTodoService.processConfirmTodoCreation(msisdn, storedUid, userInput, priorInput, field);
	}

	@RequestMapping(value = FULL_PATH + "/create/complete", method = RequestMethod.GET)
	@ResponseBody
	public Request finishTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
								   @RequestParam String storedUid) throws URISyntaxException {
		return ussdTodoService.processFinishTodoEntry(inputNumber, storedUid);
	}

    /*
    Menus to view prior todos
     */

	@RequestMapping(value = FULL_PATH + "/existing", method = RequestMethod.GET)
	public Request viewExistingTodos(@RequestParam(value = phoneNumber) String inputNumber,
									 @RequestParam(required = false) Integer page,
									 @RequestParam(required = false) Boolean fetchAll) throws URISyntaxException {
		return ussdTodoService.processViewExistingTodos(inputNumber, page, fetchAll);
	}

	/*
	View and modify a to-do entry
	 */
	@RequestMapping(value = FULL_PATH + "/view", method = RequestMethod.GET)
	public Request viewTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processViewTodoEntry(inputNumber, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/view/email", method = RequestMethod.GET)
	public Request emailTodoResponses(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processEmailTodoResponses(inputNumber, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/view/email/send", method = RequestMethod.GET)
	public Request emailResponsesDo(@RequestParam(value = phoneNumber) String inputNumber,
									@RequestParam String todoUid,
									@RequestParam(value = userInputParam) String emailAddress) throws URISyntaxException {
		return ussdTodoService.processEmailResponseDo(inputNumber, todoUid, emailAddress);
	}

	@RequestMapping(value = FULL_PATH + "/complete/prompt", method = RequestMethod.GET)
	public Request markTodoCompletePrompt(@RequestParam(value = phoneNumber) String inputNumber,
										  @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processMarkTodoCompletePrompt(inputNumber, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/complete/done", method = RequestMethod.GET)
	public Request markTodoCompleteDone(@RequestParam(value = phoneNumber) String inputNumber,
										@RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processMarkTodoCompleteDone(inputNumber, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/modify", method = RequestMethod.GET)
	public Request alterTodoMenu(@RequestParam(value = phoneNumber) String msisdn,
								 @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processAlterTodoMenu(msisdn, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/modify/subject", method = RequestMethod.GET)
	public Request alterTodoSubject(@RequestParam(value = phoneNumber) String msisdn,
									@RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processAlterTodoSubject(msisdn, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/modify/subject/done", method = RequestMethod.GET)
	public Request confirmSubjectModification(@RequestParam(value = phoneNumber) String msisdn,
											  @RequestParam String todoUid,
											  @RequestParam(value = userInputParam) String subject) throws URISyntaxException {
		return ussdTodoService.processConfirmSubjectModification(msisdn, todoUid, subject);
	}

	@RequestMapping(value = FULL_PATH + "/modify/date", method = RequestMethod.GET)
	public Request alterTodoDate(@RequestParam(value = userInputParam) String msisdn,
								 @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processAlterTodoDate(msisdn, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/modify/date/confirm", method = RequestMethod.GET)
	public Request alterDateConfirm(@RequestParam(value = phoneNumber) String msisdn,
									@RequestParam String todoUid,
									@RequestParam(value = userInputParam) String userInput,
									@RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {
		return ussdTodoService.processAlterDateConfirm(msisdn, todoUid, userInput, priorInput);
	}

	@RequestMapping(value = FULL_PATH + "/modify/date/complete", method = RequestMethod.GET)
	public Request changeEntryConfirmed(@RequestParam String msisdn,
										@RequestParam String todoUid,
										@RequestParam long epochMillis) throws URISyntaxException {
		return ussdTodoService.processChangeEntryConfirmed(msisdn, todoUid, epochMillis);
	}

	@RequestMapping(value = FULL_PATH + "/modify/cancel", method = RequestMethod.GET)
	public Request cancelTodo(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
		return ussdTodoService.processCancelTodo(msisdn, todoUid);
	}

	@RequestMapping(value = FULL_PATH + "/modify/cancel/confirm", method = RequestMethod.GET)
	public Request cancelTodoConfirmed(@RequestParam String msisdn,
									   @RequestParam String todoUid,
									   @RequestParam(value = yesOrNoParam) boolean confirmed) throws URISyntaxException {
		return ussdTodoService.processCancelTodoConfirmed(msisdn, todoUid, confirmed);
	}
}
