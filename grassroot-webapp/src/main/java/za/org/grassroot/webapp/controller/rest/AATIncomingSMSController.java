package za.org.grassroot.webapp.controller.rest;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.EventLogBroker;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by paballo on 2016/02/17.
 */

@RestController
@RequestMapping("/sms/")
public class AATIncomingSMSController {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AATIncomingSMSController.class);
    private static final String patternToMatch = "\\b(?:yes|no|abstain|maybe)\\b";

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManager;

    @Autowired
    EventLogBroker eventLogManager;

    public static final String fromNumber ="fn";
    public static final String message ="ms";


    @RequestMapping(value = "incoming", method = RequestMethod.GET)
    public void receiveSms(@RequestParam(value =fromNumber, required = true) String phoneNumber,
                                   @RequestParam(value = message,required = true) String msg) {


        log.info("Inside AATIncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);

        User user = userManager.loadOrSaveUser(phoneNumber);
        String trimmedMsg =  msg.toLowerCase().trim();

        if(user ==null || !isValidInput(trimmedMsg)){
            if (user != null) {
                eventManagementService.notifyUnableToProcessEventReply(user);
            }
            return;
        }

        boolean needsToVote = userManager.needsToVote(user);
        boolean needsToRsvp = userManager.needsToRSVP(user);

        if((needsToVote && needsToRsvp)) {
            eventManagementService.notifyUnableToProcessEventReply(user);
        } else {
            if (needsToVote) {
                List<Event> outstandingVotes = eventManagementService.getOutstandingVotesForUser(user);
                if (outstandingVotes != null && !outstandingVotes.isEmpty()) {
                    eventLogManager.rsvpForEvent(outstandingVotes.get(0).getUid(), user.getUid(), EventRSVPResponse.fromString(trimmedMsg));
                }
            }
            else if (needsToRsvp) {
                String uid = eventManagementService.getOutstandingRSVPForUser(user).get(0).getUid();
                eventLogManager.rsvpForEvent(uid, user.getUid(), EventRSVPResponse.fromString(trimmedMsg));
            }
        }

    }

    private boolean isValidInput(String message){
        Pattern regex = Pattern.compile(patternToMatch);
        Matcher regexMatcher = regex.matcher(message);
        return  regexMatcher.find();
    }


}
