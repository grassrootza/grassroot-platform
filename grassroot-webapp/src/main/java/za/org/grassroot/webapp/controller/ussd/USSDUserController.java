package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDUserController {

	private static final String keyName = "name";
	private static final String keyLanguage = "language";

	private UssdUserService ussdUserService;

	public USSDUserController(UssdUserService ussdUserService) {
		this.ussdUserService = ussdUserService;
	}

	@RequestMapping(value = homePath + "rename-start")
	@ResponseBody
	public Request renameAndStart(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = userInputParam) String userName) throws URISyntaxException {

		return ussdUserService.processRenameAndStart(inputNumber, userName);
	}

	@RequestMapping(value = homePath + userMenus + startMenu)
	@ResponseBody
	public Request userProfile(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdUserService.processUserProfile(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + keyName)
	@ResponseBody
	public Request userDisplayName(@RequestParam(value = phoneNumber, required = true) String inputNumber) throws URISyntaxException {
		return ussdUserService.processUserDisplayName(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + keyName + doSuffix)
	@ResponseBody
	public Request userChangeName(@RequestParam(value = phoneNumber, required = true) String inputNumber,
								  @RequestParam(value = userInputParam, required = true) String newName) throws URISyntaxException {
		return ussdUserService.processUserChangeName(inputNumber, newName);
	}

	@RequestMapping(value = homePath + userMenus + keyLanguage)
	@ResponseBody
	public Request userPromptLanguage(@RequestParam(value = phoneNumber, required = true) String inputNumber) throws URISyntaxException {
		return ussdUserService.processUserPromptLanguage(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + keyLanguage + doSuffix)
	@ResponseBody
	public Request userChangeLanguage(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam String language) throws URISyntaxException {

		return ussdUserService.processUserChangeLanguage(inputNumber, language);
	}

	@RequestMapping(value = homePath + userMenus + "link" + doSuffix)
	@ResponseBody
	public Request userSendAndroidLink(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdUserService.processUserSendAndroidLink(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + "email")
	@ResponseBody
	public Request alterEmailPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdUserService.processAlterEmailPrompt(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + "email/set")
	@ResponseBody
	public Request setEmail(@RequestParam(value = phoneNumber) String inputNumber,
							@RequestParam(value = userInputParam) String email) throws URISyntaxException {
		return ussdUserService.processSetEmail(inputNumber, email);
	}

	@RequestMapping(value = homePath + userMenus + "town")
	@ResponseBody
	public Request townPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdUserService.processTownPrompt(inputNumber);
	}

	@RequestMapping(value = homePath + userMenus + "town/select")
	@ResponseBody
	public Request townOptions(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam(value = userInputParam) String userInput) throws URISyntaxException {
		return ussdUserService.processTownOptions(inputNumber, userInput);
	}

	@RequestMapping(value = homePath + userMenus + "town/confirm")
	@ResponseBody
	public Request townConfirm(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam String placeId) throws URISyntaxException {
		return ussdUserService.processTownConfirm(inputNumber, placeId);
	}
}