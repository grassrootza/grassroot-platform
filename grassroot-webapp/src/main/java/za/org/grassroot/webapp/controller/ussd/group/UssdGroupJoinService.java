package za.org.grassroot.webapp.controller.ussd.group;

import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

public interface UssdGroupJoinService {
	Request processSetJoinTopics(String inputNumber, String groupUid, String topic) throws URISyntaxException;

	Request processSetUserProfileMenu(String inputNumber,
									  String field,
									  Province province,
									  Locale language,
									  String name) throws URISyntaxException;
}
