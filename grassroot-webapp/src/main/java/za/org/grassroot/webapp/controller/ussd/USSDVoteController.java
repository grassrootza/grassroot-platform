package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.enums.VoteTime;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.phoneNumber;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController {
	private static final String path = UssdSupport.homePath + UssdSupport.voteMenus;

	private final UssdVoteService ussdVoteService;

    @Autowired
    public USSDVoteController(UssdVoteService ussdVoteService) {
        this.ussdVoteService = ussdVoteService;
    }

    @RequestMapping(value = path + "respond")
    public Request respondToVote(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam String voteUid) throws URISyntaxException {
        return ussdVoteService.processRespondToVote(inputNumber, voteUid);
    }

    @RequestMapping(value = path + "description")
    public Request showVoteDescription(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam String voteUid) throws URISyntaxException {
        return ussdVoteService.processShowVoteDescription(inputNumber, voteUid);
    }

    @RequestMapping(value = path + "record")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam String voteUid, @RequestParam String response) throws URISyntaxException {
        return ussdVoteService.processVoteAndWelcome(inputNumber, voteUid, response);
    }

    /*
    Mass vote menus begin here
     */

    @RequestMapping(value = path + "mass/opening") // if we know what it is
    @ResponseBody
    public Request massVoteOpening(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam String voteUid) throws URISyntaxException {
        return ussdVoteService.processKnownMassVote(inputNumber, voteUid);
    }

    @RequestMapping(value = path + "mass/language")
    @ResponseBody
    public Request massVoteSetLanguageAndPrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                                @RequestParam String voteUid,
                                                @RequestParam Locale language) throws URISyntaxException {
        return ussdVoteService.processMassVoteLanguageSelection(inputNumber, voteUid, language);
    }

    @RequestMapping(value = path + "mass/record")
    @ResponseBody
    public Request massVoteRecordResponseAndContinue(@RequestParam(value = phoneNumber) String inputNumber,
                                                     @RequestParam String voteUid,
                                                     @RequestParam String response,
                                                     @RequestParam(required = false) Locale language) throws URISyntaxException {
        return ussdVoteService.processMassVoteResponse(inputNumber, voteUid, response, language);
    }

    /*
    Restructured menus begin here: begin with subject
     */
    @RequestMapping(value = { path + UssdSupport.startMenu, path + "subject" })
    @ResponseBody
    public Request voteSubject(@RequestParam String msisdn,
                               @RequestParam(required = false) String requestUid) throws URISyntaxException {
        return ussdVoteService.processVoteSubject(msisdn, requestUid);
    }

    @RequestMapping(value = path + "type")
    @ResponseBody
    public Request voteType(@RequestParam String msisdn, @RequestParam String request,
                            @RequestParam(required = false) String requestUid) throws URISyntaxException {
        return ussdVoteService.processVoteType(msisdn, request, requestUid);
    }

    @RequestMapping(value = path + "yes_no")
    @ResponseBody
    public Request yesNoSelectGroup(@RequestParam String msisdn, @RequestParam String requestUid) throws URISyntaxException {
        return ussdVoteService.processYesNoSelectGroup(msisdn, requestUid);
    }

    @RequestMapping(value = path + "closing")
    @ResponseBody
    public Request selectTime(@RequestParam String msisdn, @RequestParam String requestUid,
                              @RequestParam(required = false) String groupUid) throws URISyntaxException {
		return ussdVoteService.processSelectTime(msisdn, requestUid, groupUid);
	}

	@RequestMapping(value = path + "multi_option/start")
    @ResponseBody
    public Request initiateMultiOption(@RequestParam String msisdn, @RequestParam String requestUid) throws URISyntaxException {
		return ussdVoteService.processInitiateMultiOption(msisdn, requestUid);
	}

	@RequestMapping(value = path + "multi_option/add")
    @ResponseBody
    public Request addVoteOption(@RequestParam String msisdn, @RequestParam String requestUid,
                                 @RequestParam String request,
                                 @RequestParam(required = false) String priorInput) throws URISyntaxException {
		return ussdVoteService.processAddVoteOption(msisdn, requestUid, request, priorInput);
	}

    @RequestMapping(value = path + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam String msisdn,
                                    @RequestParam String requestUid) throws URISyntaxException {

		return ussdVoteService.processCustomVotingTime(msisdn, requestUid);
	}

	@RequestMapping(value = path + "confirm")
    @ResponseBody
    public Request confirmVoteSend(@RequestParam String msisdn, @RequestParam String requestUid,
                                   @RequestParam String request,
                                   @RequestParam(required = false) String priorInput,
                                   @RequestParam(required = false) String field,
                                   @RequestParam(required = false) VoteTime time,
                                   @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
		return ussdVoteService.processConfirmVoteSend(msisdn, requestUid, request, priorInput, field, time, interrupted);
	}

	@RequestMapping(value = path + "send")
    @ResponseBody
    public Request voteSendDo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam String requestUid) throws URISyntaxException {

		return ussdVoteService.processVoteSendDo(inputNumber, requestUid);
	}

	@RequestMapping(value = path + "send-reset")
    public Request voteSendResetTime(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam String requestUid) throws URISyntaxException {
		return ussdVoteService.processVoteSendResetTime(inputNumber, requestUid);
	}
}