package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.integration.sms.SmsSendingService;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.EventLogBroker;
import za.org.grassroot.services.MessageAssemblingService;
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

    private static final Logger log = LoggerFactory.getLogger(AATIncomingSMSController.class);
    private static final String patternToMatch = "\\b(?:yes|no|abstain|maybe)\\b";

    @Autowired
    EventBroker eventBroker;

    @Autowired
    UserManagementService userManager;

    @Autowired
    EventLogBroker eventLogManager;

    @Autowired
    MessageAssemblingService messageAssemblingService;

    @Autowired
    SmsSendingService smsSendingService;

    public static final String fromNumber ="fn";
    public static final String message ="ms";


    @RequestMapping(value = "incoming", method = RequestMethod.GET)
    public void receiveSms(@RequestParam(value =fromNumber, required = true) String phoneNumber,
                                   @RequestParam(value = message,required = true) String msg) {


        log.info("Inside AATIncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);

        User user = userManager.findByInputNumber(phoneNumber);
        String trimmedMsg =  msg.toLowerCase().trim();

        if(user ==null || !isValidInput(trimmedMsg)){
            if (user != null) {
                notifyUnableToProcessReply(user);
            }
            return;
        }

        boolean needsToVote = eventBroker.userHasResponsesOutstanding(user, EventType.VOTE);
        boolean needsToRsvp = eventBroker.userHasResponsesOutstanding(user, EventType.MEETING);

        if((needsToVote && needsToRsvp)) {
            notifyUnableToProcessReply(user);
        } else {
            if (needsToVote) {
                List<Event> outstandingVotes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
                if (outstandingVotes != null && !outstandingVotes.isEmpty()) {
                    eventLogManager.rsvpForEvent(outstandingVotes.get(0).getUid(), user.getUid(), EventRSVPResponse.fromString(trimmedMsg));
                }
            }
            else if (needsToRsvp) {
                String uid = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING).get(0).getUid();
                eventLogManager.rsvpForEvent(uid, user.getUid(), EventRSVPResponse.fromString(trimmedMsg));
            }
        }
    }

    private void notifyUnableToProcessReply(User user) {
        String message = messageAssemblingService.createReplyFailureMessage(user);
        smsSendingService.sendSMS(message, user.getPhoneNumber());
    }

    private boolean isValidInput(String message){
        Pattern regex = Pattern.compile(patternToMatch);
        Matcher regexMatcher = regex.matcher(message);
        return  regexMatcher.find();
    }


}
