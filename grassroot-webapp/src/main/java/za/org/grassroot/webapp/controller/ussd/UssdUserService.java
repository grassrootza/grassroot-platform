package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdUserService {
	Request processRenameAndStart(String inputNumber, String userName) throws URISyntaxException;

	Request processUserProfile(String inputNumber) throws URISyntaxException;

	Request processUserDisplayName(String inputNumber) throws URISyntaxException;

	Request processUserChangeName(String inputNumber, String newName) throws URISyntaxException;

	Request processUserPromptLanguage(String inputNumber) throws URISyntaxException;

	Request processUserChangeLanguage(String inputNumber, String language) throws URISyntaxException;

	Request processUserSendAndroidLink(String inputNumber) throws URISyntaxException;

	Request processAlterEmailPrompt(String inputNumber) throws URISyntaxException;

	Request processSetEmail(String inputNumber, String email) throws URISyntaxException;

	Request processTownPrompt(String inputNumber) throws URISyntaxException;

	Request processTownOptions(String inputNumber, String userInput) throws URISyntaxException;

	Request processTownConfirm(String inputNumber, String placeId) throws URISyntaxException;
}
