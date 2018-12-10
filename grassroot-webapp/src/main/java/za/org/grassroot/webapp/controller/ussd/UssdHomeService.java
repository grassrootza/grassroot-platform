package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdHomeService {
	Request processStartMenu(String inputNumber, String enteredUSSD) throws URISyntaxException;

	Request processForceStartMenu(String inputNumber, String trailingDigits) throws URISyntaxException;
}
