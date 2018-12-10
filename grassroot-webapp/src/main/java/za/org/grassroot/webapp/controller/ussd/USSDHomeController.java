package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
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

/**
 * Controller for the USSD menu
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDHomeController extends USSDBaseController {

//    @Setter(AccessLevel.PACKAGE) private USSDVoteController voteController;
//    @Setter(AccessLevel.PACKAGE) private USSDMeetingController meetingController;

    private final UssdHomeService ussdHomeService;

    @Value("${grassroot.ussd.code.length:9}")
    private int hashPosition;

    @Value("${grassroot.ussd.safety.suffix:911}")
    private String safetyCode;

    @Value("${grassroot.ussd.sendlink.suffix:123}")
    private String sendMeLink;

    @Value("${grassroot.ussd.promotion.suffix:44}")
    private String promotionSuffix;

    @Value("${grassroot.ussd.livewire.suffix:411}")
    private String livewireSuffix;

    @Value("${grassroot.geo.apis.enabled:false}")
    private boolean geoApisEnabled;

    @Autowired
    public USSDHomeController(UssdHomeService ussdHomeService) {
        this.ussdHomeService = ussdHomeService;
    }

    private final ExecutorService starttestExecutors = Executors.newFixedThreadPool(4);

    @RequestMapping(value = homePath + "startTest")
    public void startMenuTest() throws InterruptedException, ExecutionException {
        final List<Callable<Void>> callables = constructTestCallables();
        log.info("### Starting start test");

        Stopwatch stopwatch = Stopwatch.createStarted();
        List<Future<Void>> futures = starttestExecutors.invokeAll(callables);
        for (Future<Void> future : futures) {
            future.get();
        }
        log.info("### Lasted secs: " + stopwatch.elapsed(TimeUnit.SECONDS));
    }

    @RequestMapping(value = homePath + "startTestSingle")
    public void startMenuTestSingle(@RequestParam(value = phoneNumber) String inputNumber) throws InterruptedException, ExecutionException, URISyntaxException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        startMenu(inputNumber, "*134*19940*2446#");
        log.info("### TX outer lasted ms: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private List<Callable<Void>> constructTestCallables() {
        int amount = 250; // cannot be larger than 900

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

    @RequestMapping(value = homePath + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = userInputParam, required = false) String enteredUSSD) throws URISyntaxException {
		return this.ussdHomeService.processStartMenu(inputNumber, enteredUSSD);
	}

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = homePath + startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(required = false) String trailingDigits) throws URISyntaxException {
        return this.ussdHomeService.processForceStartMenu(inputNumber, trailingDigits);
    }


    /*
    Menus to process responses to votes and RSVPs,
     */
    @RequestMapping(value = homePath + U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        String errorMessage = messageAssembler.getMessage("ussd.error", "en");
        return menuBuilder(new USSDMenu(errorMessage, optionsHomeExit(userManager.findByInputNumber(inputNumber), false)));
    }

    @RequestMapping(value = homePath + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        String exitMessage = getMessage("exit." + promptKey, user);
        return menuBuilder(new USSDMenu(exitMessage));
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
        return tooLongError;
    }
}