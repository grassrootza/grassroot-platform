package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.homePath;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.startMenu;

/**
 * Created by luke on 2017/05/07.
 */
@Slf4j
@RestController
@RequestMapping(path = "/ussd/livewire/", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDLiveWireController {

	private final UssdLiveWireService ussdLiveWireService;

	public USSDLiveWireController(UssdLiveWireService ussdLiveWireService) {
		this.ussdLiveWireService = ussdLiveWireService;
	}

	@RequestMapping(value = homePath + startMenu + "_livewire")
	@ResponseBody
	public Request liveWirePageMenu(@RequestParam String msisdn, @RequestParam int page) throws URISyntaxException {
		return ussdLiveWireService.processLiveWIrePageMenu(msisdn, page);
	}

	@RequestMapping("mtg")
	@ResponseBody
	public Request selectContactForMeeting(@RequestParam String msisdn,
										   @RequestParam(required = false) String mtgUid,
										   @RequestParam(required = false) String alertUid) throws URISyntaxException {
		return ussdLiveWireService.processSelectContactForMeeting(msisdn, mtgUid, alertUid);
	}

	@RequestMapping("instant")
	@ResponseBody
	public Request selectGroupForInstantAlert(@RequestParam String msisdn,
											  @RequestParam(required = false) Integer pageNumber) throws URISyntaxException {
		return ussdLiveWireService.processSelectGroupForInstantAlert(msisdn, pageNumber);
	}

	@RequestMapping("register")
	@ResponseBody
	public Request promptToRegisterAsContact(@RequestParam String msisdn) throws URISyntaxException {
		return ussdLiveWireService.processPromptToRegisterAsContact(msisdn);
	}

	@RequestMapping("register/do")
	@ResponseBody
	public Request registerAsLiveWireContact(@RequestParam String msisdn,
											 @RequestParam boolean location) throws URISyntaxException {
		return ussdLiveWireService.processRegisterAsLiveWireContact(msisdn, location);
	}

	@RequestMapping("group")
	public Request groupChosen(@RequestParam String msisdn,
							   @RequestParam(required = false) String groupUid,
							   @RequestParam(required = false) String alertUid) throws URISyntaxException {
		return ussdLiveWireService.processGroupChosen(msisdn, groupUid, alertUid);
	}

	@RequestMapping("contact/phone")
	@ResponseBody
	public Request enterContactPersonNumber(@RequestParam String msisdn,
											@RequestParam String alertUid,
											@RequestParam(required = false) Boolean revising) throws URISyntaxException {
		return ussdLiveWireService.processEnterContactPersonNumber(msisdn, alertUid, revising);
	}

	@RequestMapping("contact/name")
	@ResponseBody
	public Request enterContactPersonName(@RequestParam String msisdn,
										  @RequestParam String alertUid,
										  @RequestParam String request,
										  @RequestParam(required = false) String priorInput,
										  @RequestParam(required = false) String contactUid,
										  @RequestParam(required = false) Boolean revising) throws URISyntaxException {
		return ussdLiveWireService.processEnterContactPersonName(msisdn, alertUid, request, priorInput, contactUid, revising);
	}

	@RequestMapping("headline")
	@ResponseBody
	public Request enterDescription(@RequestParam String msisdn, @RequestParam String alertUid,
									@RequestParam String request,
									@RequestParam(required = false) String contactUid,
									@RequestParam(required = false) String priorInput) throws URISyntaxException {
		return ussdLiveWireService.processEnterDescription(msisdn, alertUid, request, contactUid, priorInput);
	}

	@RequestMapping("destination")
	@ResponseBody
	public Request chooseList(@RequestParam String msisdn, @RequestParam String alertUid,
							  @RequestParam String request,
							  @RequestParam(required = false) String priorInput) throws URISyntaxException {
		return ussdLiveWireService.processChooseList(msisdn, alertUid, request, priorInput);
	}

	@RequestMapping("confirm")
	@ResponseBody
	public Request confirmAlert(@RequestParam String msisdn, @RequestParam String alertUid,
								@RequestParam String request,
								@RequestParam String field,
								@RequestParam(required = false) String priorInput,
								@RequestParam(required = false) String destType,
								@RequestParam(required = false) String destinationUid,
								@RequestParam(required = false) Boolean revisingContact,
								@RequestParam(required = false) String contactUid) throws URISyntaxException {
		return ussdLiveWireService.processConfirmAlert(msisdn, alertUid, request, field, priorInput, destType, destinationUid, contactUid);
	}

	@RequestMapping("send")
	@ResponseBody
	public Request sendAlert(@RequestParam String msisdn, @RequestParam String alertUid,
							 @RequestParam boolean location) throws URISyntaxException {
		return ussdLiveWireService.processSendAlert(msisdn, alertUid, location);
	}
}
