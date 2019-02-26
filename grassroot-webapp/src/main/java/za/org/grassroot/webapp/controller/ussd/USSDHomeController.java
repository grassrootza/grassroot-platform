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

	public USSDHomeController(UssdHomeService ussdHomeService, UssdSupport ussdSupport) {
		this.ussdHomeService = ussdHomeService;
		this.ussdSupport = ussdSupport;
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
        log.info("Triggering start force, trailing digits: {}", trailingDigits);
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
        return ussdHomeService.processTestQuestion();
    }

    @RequestMapping(value = homePath + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return ussdSupport.tooLongError;
    }
}