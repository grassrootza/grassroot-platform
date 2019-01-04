package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.TownLookupResult;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@Slf4j
@RequestMapping(value = "/ussd/geo", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@ConditionalOnProperty(name = "grassroot.geo.apis.enabled")
public class USSDGeoApiController extends USSDBaseController {

	private final LocationInfoBroker locationInfoBroker;
	private final USSDMessageAssembler messageAssembler;
	private final UssdGeoApiService ussdGeoApiService;

	@Autowired
	public USSDGeoApiController(LocationInfoBroker locationInfoBroker, USSDMessageAssembler messageAssembler, UssdGeoApiService ussdGeoApiService) {
		this.locationInfoBroker = locationInfoBroker;
		this.messageAssembler = messageAssembler;
		this.ussdGeoApiService = ussdGeoApiService;
	}

	// for mapping USSD code directly to this
	@RequestMapping(value = "/opening/{dataSet}", method = RequestMethod.GET)
	public Request openingMenu(@PathVariable String dataSet, @RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam(required = false) Boolean forceOpening) throws URISyntaxException {
		User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		boolean possiblyInterrupted = forceOpening == null || !forceOpening;
		if (possiblyInterrupted && cacheManager.fetchUssdMenuForUser(inputNumber) != null) {
			String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
			USSDMenu promptMenu = new USSDMenu(getMessage("home.start.prompt-interrupted", user));
			promptMenu.addMenuOption(returnUrl, getMessage("home.start.interrupted.resume", user));
			promptMenu.addMenuOption("geo/opening/" + dataSet + "?forceOpening=true", getMessage("home.start.interrupted.start", user));
			return menuBuilder(promptMenu);
		} else {
			return menuBuilder(this.ussdGeoApiService.openingMenu(convert(user), dataSet));
		}
	}

	@RequestMapping(value = "/infoset")
	public Request chooseInfoSet(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam String dataSet,
								 @RequestParam(required = false) Locale language,
								 @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
		return ussdGeoApiService.chooseInfoSet(inputNumber, dataSet, language, interrupted);
	}

	@RequestMapping(value = "/location/province/{dataSet}/{infoSet}")
	public Request chooseProvince(@RequestParam(value = phoneNumber) String inputNumber,
								  @PathVariable String dataSet,
								  @PathVariable String infoSet,
								  @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
		return ussdGeoApiService.chooseProvinceMenu(inputNumber, dataSet, infoSet, interrupted);
	}

	@RequestMapping(value = "/location/place/{dataSet}/{infoSet}")
	public Request enterTown(@RequestParam(value = phoneNumber) String inputNumber,
							 @PathVariable String dataSet,
							 @PathVariable String infoSet) throws URISyntaxException {
		return ussdGeoApiService.enterTownMenu(inputNumber, dataSet, infoSet);
	}


	@RequestMapping(value = "/location/place/select/{dataSet}/{infoSet}")
	public Request selectTownAndSend(@RequestParam(value = phoneNumber) String inputNumber,
									 @PathVariable String dataSet,
									 @PathVariable String infoSet,
									 @RequestParam(value = userInputParam) String userInput) throws URISyntaxException {
		return ussdGeoApiService.selectTownAndSendMenu(inputNumber, dataSet, infoSet, userInput);
	}

	@RequestMapping(value = "/info/send/place/{dataSet}/{infoSet}")
	private Request sendInfoForPlace(@RequestParam(value = phoneNumber) String inputNumber,
									 @PathVariable String dataSet,
									 @PathVariable String infoSet,
									 @RequestParam String placeId) throws URISyntaxException {
		return ussdGeoApiService.sendInfoForPlace(inputNumber, dataSet, infoSet, placeId);
	}

	@RequestMapping(value = "/info/send/province/{dataSet}/{infoSet}")
	public Request sendInfoForProvince(@RequestParam(value = phoneNumber) String inputNumber,
									   @PathVariable String dataSet,
									   @PathVariable String infoSet,
									   @RequestParam Province province) throws URISyntaxException {
		UserMinimalProjection user = userManager.findUserMinimalAndStashMenu(inputNumber, null);
		return menuBuilder(sendMessageWithInfo(dataSet, infoSet, province, user));
	}

	private USSDMenu sendMessageWithInfo(String dataSet, String infoTag, Province province, UserMinimalProjection user) {
		List<String> records = locationInfoBroker.retrieveRecordsForProvince(dataSet, infoTag, province, user.getLocale());
		final String prompt = messageAssembler.getMessage(dataSet + ".sent.prompt",
				new String[]{String.valueOf(records.size())}, user);
		locationInfoBroker.assembleAndSendRecordMessage(dataSet, infoTag, province, user.getUid());
		return new USSDMenu(prompt); // todo : include option to send safety alert if they are on? (and v/versa)
	}
}
