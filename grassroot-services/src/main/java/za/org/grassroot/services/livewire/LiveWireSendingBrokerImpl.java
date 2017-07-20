package za.org.grassroot.services.livewire;

import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.integration.livewire.LiveWirePushBroker;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2017/05/08.
 */
@Service
public class LiveWireSendingBrokerImpl implements LiveWireSendingBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWireSendingBroker.class);

    @Value("${grassroot.livewire.public.path:http://localhost:8080/livewire/public/}")
    private String publicInfoPath;

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy");
    private static final DateTimeFormatter mtgFormat = DateTimeFormatter.ofPattern("dd MM, HH:mm");

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
    @Transactional(readOnly = true)
    public void sendLiveWireAlerts(Set<String> alertUids) {
        final List<String> publicPushMail = subscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.SYSTEM.name());
        logger.debug("Processing {} LiveWire alerts", alertUids.size(), publicPushMail.size());
        alertUids.forEach(u -> sendAlert(alertRepository.findOneByUid(u), publicPushMail));
    }

    private void sendAlert(LiveWireAlert alert, List<String> systemEmailAddresses) {
        logger.debug("starting to send alert, uids: {}", alert.getPublicListUids());
        // send the alert (maybe add Twitter etc in future)
        List<String> alertEmails = new ArrayList<>(systemEmailAddresses);
        switch (alert.getDestinationType()) {
            case SINGLE_LIST:
                alertEmails.addAll(alert.getTargetSubscriber().getPushEmails());
                break;
            case PUBLIC_LIST:
                alertEmails.addAll(collectPublicEmailAddresses(alert));
                break;
            case SINGLE_AND_PUBLIC:
                alertEmails.addAll(alert.getTargetSubscriber().getPushEmails());
                alertEmails.addAll(collectPublicEmailAddresses(alert));
                break;
        }
        liveWirePushBroker.sendLiveWireEmails(alert.getUid(), generateEmailsForAlert(alert, alertEmails));
        logger.info("LiveWire of type {} sent to {} emails! Description: {}. Setting to sent ...", alert.getDestinationType(), alertEmails.size(), alert.getDescription());
    }

    private List<String> collectPublicEmailAddresses(LiveWireAlert alert) {
        // doing this in one query would be more efficient, but because of the unnest it can't be done with
        // JPQL, and passing in the list of UIDs is then difficult in JPA, hence ...
        return alert.getPublicListUids()
                .stream()
                .map(u -> subscriberRepository.findOneByUid(u).getPushEmails())
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<GrassrootEmail> generateEmailsForAlert(LiveWireAlert alert, List<String> emailAddresses) {
        String subject, template;
        Map<String, Object> emailVars = new HashedMap<>();

        emailVars.put("contactName", alert.getContactNameNullSafe());
        logger.debug("contactName, and from alert: {}", alert.getContactNameNullSafe(),
                alert.getContactUser().getName());
        emailVars.put("contactNumber", PhoneNumberUtil.formattedNumber(
                alert.getContactUser().getPhoneNumber()));
        emailVars.put("description", alert.getDescription());

        logger.debug("formatted number: {}", emailVars.get("contactNumber"));

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
            emailVars.put("dateTime", mtgFormat.format(meeting.getEventDateTimeAtSAST()));
            emailVars.put("mtgSubject", meeting.getName());
            populateGroupVars(meeting.getAncestorGroup(), emailVars);
        }

        final Context ctx = new Context(Locale.ENGLISH);

        return emailAddresses.stream()
                .map(a -> finishAlert(ctx, emailVars, subject, template, a))
                .collect(Collectors.toList());
    }

    private GrassrootEmail finishAlert(final Context ctx,
                                       Map<String, Object> emailVars,
                                       String subject,
                                       String template,
                                       String emailAddress) {
        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder();
        try {
            final String encodedEmail = URLEncoder.encode(emailAddress, "UTF-8");
            emailVars.put("infoLink", publicInfoPath + "info?email=" + encodedEmail);
            emailVars.put("unsubLink", publicInfoPath + "unsubscribe?email=");
        } catch (UnsupportedEncodingException e) {
            logger.error("Unsupported encoding!", e);
            emailVars.put("infoLink", publicInfoPath + "info");
            emailVars.put("unsubLink", publicInfoPath + "info"); // better than nothing
        }

        ctx.setVariables(emailVars);
        builder.from("Grassroot LiveWire")
                .address(emailAddress)
                .subject(messageSource.getMessage(subject))
                .content(templateEngine.process("text/" + template, ctx))
                .htmlContent(templateEngine.process("html/" + template, ctx));
        return builder.build();
    }

    private void populateGroupVars(Group group, Map<String, Object> emailVars) {
        emailVars.put("numberMembers", group.getMembers().size());
        emailVars.put("lengthTime", dateFormat.format(group.getCreatedDateTimeAtSAST()));
        emailVars.put("numberTasks",
                String.valueOf(group.getDescendantEvents().size() + group.getDescendantTodos().size()));
    }

}
