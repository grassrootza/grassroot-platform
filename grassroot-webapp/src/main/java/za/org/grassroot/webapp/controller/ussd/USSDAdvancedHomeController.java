package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Controller for the USSD menu more option
 */
@Slf4j
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDAdvancedHomeController {
	private static final String ROOT_PATH = homePath + moreMenus;

	private final UssdAdvancedHomeService ussdAdvancedHomeService;

	@Autowired
	public USSDAdvancedHomeController(UssdAdvancedHomeService ussdAdvancedHomeService) {
		this.ussdAdvancedHomeService = ussdAdvancedHomeService;
	}

	@RequestMapping(value = ROOT_PATH + startMenu)
	@ResponseBody
	public Request moreOptions(@RequestParam String msisdn) throws URISyntaxException {
		return ussdAdvancedHomeService.processMoreOptions(msisdn);
	}

	@RequestMapping(value = ROOT_PATH + "/public/mtgs")
	@ResponseBody
	public Request getPublicMeetingsNearUser(@RequestParam(value = phoneNumber) String inputNumber,
											 @RequestParam(required = false) Integer page,
											 @RequestParam(required = false) boolean repeat) throws URISyntaxException {
		return ussdAdvancedHomeService.processGetPublicMeetingNearUser(inputNumber, page, repeat);
	}

	@RequestMapping(value = ROOT_PATH + "/public/mtgs/details")
	@ResponseBody
	public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam String meetingUid) throws URISyntaxException {
		return ussdAdvancedHomeService.processMeetingDetails(inputNumber, meetingUid);
	}

	@RequestMapping(value = homePath + moreMenus + startMenu + "/track-me")
	@ResponseBody
	public Request trackMe(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdAdvancedHomeService.processTrackMe(inputNumber);
	}
}
