package za.org.grassroot.webapp.controller.ussd;

import java.net.URISyntaxException;

import za.org.grassroot.webapp.model.ussd.AAT.Request;

public interface UssdService {
	Request processStart(final String inputNumber, final String enteredUSSD) throws URISyntaxException;
}
