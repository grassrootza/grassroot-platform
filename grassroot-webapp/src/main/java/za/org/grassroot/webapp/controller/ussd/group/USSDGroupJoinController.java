package za.org.grassroot.webapp.controller.ussd.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupJoinController {

	private final UssdGroupJoinService ussdGroupJoinService;

	public USSDGroupJoinController(UssdGroupJoinService ussdGroupJoinService) {
		this.ussdGroupJoinService = ussdGroupJoinService;
	}

	@RequestMapping(value = homePath + "group/join/topics")
	@ResponseBody
	public Request setJoinTopics(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam String groupUid,
								 @RequestParam String topic) throws URISyntaxException {
		return this.ussdGroupJoinService.processSetJoinTopics(inputNumber, groupUid, topic);
	}

	@RequestMapping(value = homePath + "group/join/profile")
	@ResponseBody
	public Request setUserProfileMenu(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam(value = "field") String field,
									  @RequestParam(value = "province", required = false) Province province,
									  @RequestParam(value = "language", required = false) Locale language,
									  @RequestParam(value = userInputParam, required = false) String name) throws URISyntaxException {
		return this.ussdGroupJoinService.processSetUserProfileMenu(inputNumber, field, province, language, name);
	}
}
