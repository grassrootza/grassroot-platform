package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.homePath;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.phoneNumber;

/**
 * Controller for the USSD menu
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDHomeController {
    private final UssdHomeService ussdHomeService;
    private final UssdSupport ussdSupport;
    private final GroupBroker groupBroker;

	public USSDHomeController(UssdHomeService ussdHomeService, UssdSupport ussdSupport, GroupBroker groupBroker) {
		this.ussdHomeService = ussdHomeService;
		this.ussdSupport = ussdSupport;
		this.groupBroker = groupBroker;
	}

	/**
     * TEST METHODS --------------------------------------------------------------
     */

    @RequestMapping(value = homePath + "startSingleThreadTest")
    public void startSingleThreadTest() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (int i = 0; i < 10; i++) {
            System.out.println("### -------------- Iteration nr." + i);
            Stopwatch txStopwatch = Stopwatch.createStarted();
            this.groupBroker.testMembersFetch(1L);
            System.out.println("### TX lasted ms: " + txStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
        log.info("### Complete startMembersTest lasted ms: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @RequestMapping(value = homePath + "startSingleNumberTest")
    public void startSingleNumberTest(@RequestParam(value = phoneNumber) String inputNumber) throws InterruptedException, ExecutionException, URISyntaxException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        startMenu(inputNumber, "*134*19940*2446#");
        log.info("### TX outer lasted ms: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @RequestMapping(value = homePath + "startMultiThreadTest")
    public void startMultiThreadTest() throws InterruptedException, ExecutionException {
        final List<Callable<Void>> callables = constructTestCallables(2);
        log.info("### Starting start test");

        Stopwatch stopwatch = Stopwatch.createStarted();
        List<Future<Void>> futures = startTestExecutors.invokeAll(callables);
        for (Future<Void> future : futures) {
            future.get();
        }
        log.info("### Lasted secs: " + stopwatch.elapsed(TimeUnit.SECONDS));
    }

    private final ExecutorService startTestExecutors = Executors.newFixedThreadPool(4);

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
                    startMenu(msisdn, "*134*19940*2446#");
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


    /**
     * -------------------------------------------------------------
     */


    @RequestMapping(value = homePath + UssdSupport.startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = UssdSupport.userInputParam, required = false) String enteredUSSD) throws URISyntaxException {
		return this.ussdHomeService.processStartMenu(inputNumber, enteredUSSD);
	}

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = homePath + UssdSupport.startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(required = false) String trailingDigits) throws URISyntaxException {
        return this.ussdHomeService.processForceStartMenu(inputNumber, trailingDigits);
    }


    /*
    Menus to process responses to votes and RSVPs,
     */
    @RequestMapping(value = homePath + UssdSupport.U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdHomeService.processNotBuilt(inputNumber);
	}

	@RequestMapping(value = homePath + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        return ussdHomeService.processExitScreen(inputNumber);
    }

    @RequestMapping(value = homePath + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1, 1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"), true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = homePath + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return ussdSupport.tooLongError;
    }
}