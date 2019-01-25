package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.phoneNumber;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.userInputParam;

@RestController
@Slf4j
@RequestMapping(value = "/ussd/geo", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@ConditionalOnProperty(name = "grassroot.geo.apis.enabled")
public class USSDGeoApiController {

	private final UssdGeoApiService ussdGeoApiService;

	@Autowired
	public USSDGeoApiController(UssdGeoApiService ussdGeoApiService) {
		this.ussdGeoApiService = ussdGeoApiService;
	}

	// for mapping USSD code directly to this
	@RequestMapping(value = "/opening/{dataSet}", method = RequestMethod.GET)
	public Request openingMenu(@PathVariable String dataSet, @RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam(required = false) Boolean forceOpening) throws URISyntaxException {
		return ussdGeoApiService.processOpeningMenu(dataSet, inputNumber, forceOpening);
	}

	@RequestMapping(value = "/infoset")
	public Request chooseInfoSet(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam String dataSet,
								 @RequestParam(required = false) Locale language,
								 @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
		return ussdGeoApiService.processChooseInfoSet(inputNumber, dataSet, language, interrupted);
	}

	@RequestMapping(value = "/location/province/{dataSet}/{infoSet}")
	public Request chooseProvince(@RequestParam(value = phoneNumber) String inputNumber,
								  @PathVariable String dataSet,
								  @PathVariable String infoSet,
								  @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
		return ussdGeoApiService.processChooseProvinceMenu(inputNumber, dataSet, infoSet, interrupted);
	}

	@RequestMapping(value = "/location/place/{dataSet}/{infoSet}")
	public Request enterTown(@RequestParam(value = phoneNumber) String inputNumber,
							 @PathVariable String dataSet,
							 @PathVariable String infoSet) throws URISyntaxException {
		return ussdGeoApiService.processEnterTownMenu(inputNumber, dataSet, infoSet);
	}


	@RequestMapping(value = "/location/place/select/{dataSet}/{infoSet}")
	public Request selectTownAndSend(@RequestParam(value = phoneNumber) String inputNumber,
									 @PathVariable String dataSet,
									 @PathVariable String infoSet,
									 @RequestParam(value = userInputParam) String userInput) throws URISyntaxException {
		return ussdGeoApiService.processSelectTownAndSendMenu(inputNumber, dataSet, infoSet, userInput);
	}

	@RequestMapping(value = "/info/send/place/{dataSet}/{infoSet}")
	public Request sendInfoForPlace(@RequestParam(value = phoneNumber) String inputNumber,
									@PathVariable String dataSet,
									@PathVariable String infoSet,
									@RequestParam String placeId) throws URISyntaxException {
		return ussdGeoApiService.processSendInfoForPlace(inputNumber, dataSet, infoSet, placeId);
	}

	@RequestMapping(value = "/info/send/province/{dataSet}/{infoSet}")
	public Request sendInfoForProvince(@RequestParam(value = phoneNumber) String inputNumber,
									   @PathVariable String dataSet,
									   @PathVariable String infoSet,
									   @RequestParam Province province) throws URISyntaxException {
		return ussdGeoApiService.processSendInfoForProvince(inputNumber, dataSet, infoSet, province);
	}
}
