package za.org.grassroot.services.livewire;

import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.integration.livewire.LiveWirePushBroker;

import java.util.*;

/**
 * Created by luke on 2017/05/08.
 */
@Service
public class LiveWireSendingBrokerImpl implements LiveWireSendingBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireSendingBroker.class);

    private final LiveWireAlertRepository alertRepository;
    private final DataSubscriberRepository subscriberRepository;
    private final LiveWirePushBroker liveWirePushBroker;
    private final MessageSourceAccessor messageSource;
    private final TemplateEngine templateEngine;

    @Autowired
    public LiveWireSendingBrokerImpl(LiveWireAlertRepository alertRepository,
                                     DataSubscriberRepository subscriberRepository,
                                     LiveWirePushBroker liveWirePushBroker,
                                     @Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSource,
                                     @Qualifier("emailTemplateEngine") TemplateEngine templateEngine) {
        this.alertRepository = alertRepository;
        this.subscriberRepository = subscriberRepository;
        this.liveWirePushBroker = liveWirePushBroker;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    @Override
    @Transactional
    public void sendLiveWireAlerts(Set<String> alertUids) {
        final List<String> allPushEmails = subscriberRepository.findAllActiveSubscriberPushEmails();
        logger.info("Processing {} alerts, to {} email addresses", alertUids.size(), allPushEmails.size());
        alertUids.forEach(u -> sendAlert(alertRepository.findOneByUid(u), allPushEmails));
    }

    private void sendAlert(LiveWireAlert alert, List<String> emailAddresses) {
        DebugUtil.transactionRequired("");
        // send the alert (maybe add Twitter etc in future)
        boolean sent = liveWirePushBroker.sendLiveWireEmails(generateEmailsForAlert(alert, emailAddresses));
        logger.info("Sent out LiveWire alert! Description: {}. Setting to sent ...", alert.getDescription());
        alert.setSent(sent);
    }

    private List<GrassrootEmail> generateEmailsForAlert(LiveWireAlert alert, List<String> emailAddresses) {
        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder();

        String subject, template;
        Map<String, Object> emailVars = new HashedMap<>();

        emailVars.put("contactName", alert.getContactNameNullSafe());
        emailVars.put("contactNumber", PhoneNumberUtil.formattedNumber(
                alert.getContactUser().getPhoneNumber()));
        emailVars.put("description", alert.getDescription());

        logger.info("formatted number: {}", emailVars.get("contactNumber"));

        // todo : check both for location
        if (LiveWireAlertType.INSTANT.equals(alert.getType())) {
            Group group = alert.getGroup();
            subject = "email.livewire.instant.subject";
            template = "livewire_instant";
            populateGroupVars(group, emailVars);
        } else {
            Meeting meeting = alert.getMeeting();
            subject = "email.livewire.meeting.subject";
            template = "livewire_meeting";
            emailVars.put("mtgLocation", meeting.getEventLocation());
            emailVars.put("dateTime", meeting.getEventDateTimeAtSAST());
            emailVars.put("mtgSubject", meeting.getName());
            populateGroupVars(meeting.getAncestorGroup(), emailVars);
        }

        final Context ctx = new Context(Locale.getDefault());
        ctx.setVariables(emailVars);
        builder.subject(messageSource.getMessage(subject))
                .content(templateEngine.process("text/" + template, ctx))
                .htmlContent(templateEngine.process("html/" + template, ctx));

        List<GrassrootEmail> emails = new ArrayList<>();
        emailAddresses.forEach(a -> {
            emails.add(builder.address(a).build());
        });
        return emails;
    }

    private void populateGroupVars(Group group, Map<String, Object> emailVars) {
        emailVars.put("numberMembers", group.getMembers().size());
        emailVars.put("lengthTime", group.getCreatedDateTime());
        emailVars.put("numberTasks",
                String.valueOf(group.getDescendantEvents().size() + group.getDescendantTodos().size()));
    }

}
