package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

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

	/**
	 * TEST METHODS --------------------------------------------------------------
	 */

	final String dataSet = "IZWE_LAMI_CONS";

	@RequestMapping(value = "startSingleThreadTest")
	public void startSingleThreadTest() throws URISyntaxException {
		Stopwatch stopwatch = Stopwatch.createStarted();
		for (int i = 0; i < 10; i++) {
			System.out.println("### -------------- Iteration nr." + i);
			Stopwatch txStopwatch = Stopwatch.createStarted();
			this.ussdGeoApiService.processOpeningMenu(dataSet, "27813074085", false);
			System.out.println("### TX lasted ms: " + txStopwatch.elapsed(TimeUnit.MILLISECONDS));
		}
		log.info("### Complete startMembersTest lasted ms: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

	@RequestMapping(value = "startSingleNumberTest")
	public void startSingleNumberTest(@RequestParam(value = phoneNumber) String inputNumber) throws InterruptedException, ExecutionException, URISyntaxException {
		Stopwatch stopwatch = Stopwatch.createStarted();
		openingMenu(dataSet, inputNumber, false);
		log.info("### TX outer lasted ms: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

	@RequestMapping(value = "startMultiThreadTest")
	public void startMultiThreadTest() throws InterruptedException, ExecutionException {
		final List<Callable<Void>> callables = constructTestCallables(10);
		log.info("### Starting start test");

		Stopwatch stopwatch = Stopwatch.createStarted();
/*
        List<Future<Void>> futures = startTestExecutors.invokeAll(callables);
        for (Future<Void> future : futures) {
            future.get();
        }
*/
		for (Callable<Void> callable : callables) {
			try {
				callable.call();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		log.info("### Lasted secs: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

	private final ExecutorService startTestExecutors = Executors.newFixedThreadPool(1);

	private List<Callable<Void>> constructTestCallables(int amount ) {
		// amount cannot be larger than 900

		final Random random = new Random(System.currentTimeMillis());
		final long startNumber = 100000 + random.nextInt(900000);

		final List<Callable<Void>> callables = new ArrayList<>();
		for (long i = 0; i < amount; i++) {
			final long iFinal = i;
			final Callable<Void> callable = () -> {
				final String msisdn = "27" + startNumber + (100L + iFinal);
				log.info("### Start test msisdn = " + msisdn);
				try {

					Stopwatch stopwatch = Stopwatch.createStarted();
					openingMenu(dataSet, msisdn, false);
					log.info("### TX outer = " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
				}
				catch (URISyntaxException e) {
					log.error("Error for number " + msisdn + ": " + e.getMessage(), e);
				}
				return null;
			};
			callables.add(callable);
		}
		return callables;
	}



	/*
	TESTING METHO
	 */
	@RequestMapping(value = "/test/{dataSet}", method = RequestMethod.GET)
	public Request testForLeak(@PathVariable String dataSet) throws URISyntaxException {
		final List<String> testNumbers = IntStream.range(1000, 9999)
				.mapToObj(num -> "2780307" + num).collect(Collectors.toList());
		return ussdGeoApiService.processOpeningMenu(dataSet, testNumbers.get(0), false);
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
