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
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.dto.GrassrootEmail;
import za.org.grassroot.core.enums.DataSubscriberType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.repository.DataSubscriberRepository;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;

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

    @Value("${grassroot.livewire.from.address:livewire@grassroot.org.za}")
    private String livewireEmailAddress;

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy");
    private static final DateTimeFormatter mtgFormat = DateTimeFormatter.ofPattern("dd MM, HH:mm");

    private final LiveWireAlertRepository alertRepository;
    private final DataSubscriberRepository subscriberRepository;

    private final MessagingServiceBroker messagingServiceBroker;
    private final MessageSourceAccessor messageSource;

    private TemplateEngine templateEngine;

    @Autowired
    public LiveWireSendingBrokerImpl(LiveWireAlertRepository alertRepository,
                                     DataSubscriberRepository subscriberRepository,
                                     MessagingServiceBroker messagingServiceBroker,
                                     @Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSource) {
        this.alertRepository = alertRepository;
        this.subscriberRepository = subscriberRepository;
        this.messagingServiceBroker = messagingServiceBroker;
        this.messageSource = messageSource;
    }

    @Autowired(required = false)
    public void setTemplateEngine(@Qualifier("emailTemplateEngine") TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // removed async as it was causing a seriously weird bug in here
    @Override
    @Transactional
    public void sendLiveWireAlerts(Set<String> alertUids) {
        logger.info("starting to process {} alerts : ", alertUids.size());
        final List<String> publicPushMail = subscriberRepository.findAllActiveSubscriberPushEmails(DataSubscriberType.SYSTEM.name());
        alertUids.forEach(u -> sendAlert(alertRepository.findOneByUid(u), publicPushMail));
    }

    private void sendAlert(LiveWireAlert alert, List<String> systemEmailAddresses) {
        logger.info("starting to send alert, uids: {}", alert.getPublicListsUids());

        List<String> emailAddresses = new ArrayList<>(systemEmailAddresses);
        emailAddresses.addAll(getAddressesForAlert(alert));

        List<GrassrootEmail> emails = generateEmailsForAlert(alert, emailAddresses);
        emails.forEach(messagingServiceBroker::sendEmail);
        alert.setSent(true);

        logger.info("LiveWire of type {} sent to {} emails! Headline : {}. Setting to sent ...",
                alert.getDestinationType(), emails.size(), alert.getHeadline());
    }

    private List<String> getAddressesForAlert(LiveWireAlert alert) {
        List<String> emailList = new ArrayList<>();
        switch (alert.getDestinationType()) {
            case SINGLE_LIST:
                if (alert.getTargetSubscriber() != null) {
                    emailList.addAll(alert.getTargetSubscriber().getPushEmails());
                }
                break;
            case PUBLIC_LIST:
                emailList.addAll(collectPublicEmailAddresses(alert));
                break;
            case SINGLE_AND_PUBLIC:
                if (alert.getTargetSubscriber() != null) {
                    emailList.addAll(alert.getTargetSubscriber().getPushEmails());
                }
                emailList.addAll(collectPublicEmailAddresses(alert));
                break;
            default:
                logger.error("invalid livewire destination type used");
                break;
        }
        return emailList;
    }

    private List<String> collectPublicEmailAddresses(LiveWireAlert alert) {
        // doing this in one query would be more efficient, but because of the unnest it can't be done with
        // JPQL, and passing in the list of UIDs is then difficult in JPA, hence ...
        // logger.info("alert public uids: {}, empty? : {}", alert.getPublicListUids(), !alert.hasPublicListUids());
        logger.info("collecting addresses, list UIDs = {}", alert.getPublicListsUids());
        List<String> addresses = alert.getPublicListsUids()
                .stream()
                .map(u -> subscriberRepository.findOneByUid(u).getPushEmails())
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        logger.info("finished collecting addresses, list UIDs = {}", alert.getPublicListsUids());
        return addresses;
    }

    private List<GrassrootEmail> generateEmailsForAlert(LiveWireAlert alert, List<String> emailAddresses) {
        final Context ctx = new Context(Locale.ENGLISH);

        String subjectKey;
        String template;

        Map<String, Object> emailVars = setBasicVariables(alert);

        if (LiveWireAlertType.INSTANT.equals(alert.getType())) {
            Group group = alert.getGroup();
            subjectKey = "email.livewire.instant.subject";
            template = "livewire_instant";
            populateGroupVars(group, emailVars);
        } else {
            Meeting meeting = alert.getMeeting();
            subjectKey = "email.livewire.meeting.subject";
            template = "livewire_meeting";
            emailVars.put("mtgLocation", meeting.getEventLocation());
            emailVars.put("dateTime", mtgFormat.format(meeting.getEventDateTimeAtSAST()));
            emailVars.put("mtgSubject", meeting.getName());
            populateGroupVars(meeting.getAncestorGroup(), emailVars);
        }

        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder();

        final String subjectLine = messageSource.getMessage(subjectKey, new String[] {alert.getHeadline()});
        builder.fromAddress(livewireEmailAddress)
                .fromName("Grassroot LiveWire")
                .subject(subjectLine);

        alert.getMediaFiles().forEach(record -> builder.attachmentByKey(record.getUid(), record.getFileName()));

        return emailAddresses.stream()
                .map(a -> finishAlert(ctx, builder, emailVars, template, a)).collect(Collectors.toList());
    }

    private Map<String, Object> setBasicVariables(LiveWireAlert alert) {
        Map<String, Object> emailVars = new HashedMap<>();

        emailVars.put("contactName", alert.getContactNameNullSafe());
        logger.debug("contactName, and from alert: {}", alert.getContactNameNullSafe(),
                alert.getContactUser().getName());
        emailVars.put("contactNumber", PhoneNumberUtil.formattedNumber(
                alert.getContactUser().getPhoneNumber()));
        emailVars.put("headline", alert.getHeadline());
        emailVars.put("description", alert.getDescription());

        return emailVars;
    }

    private GrassrootEmail finishAlert(Context ctx, GrassrootEmail.EmailBuilder builder,
                                       Map<String, Object> emailVars,
                                       String template,
                                       String emailAddress) {
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
        logger.info("About to generate mail, variables = {}", ctx.getVariableNames());

        final String textContent = templateEngine.process("text/" + template, ctx);
        final String htmlContent = templateEngine.process("html/" + template, ctx);

        logger.info("Exiting email creation, html content = {}", htmlContent);

        return builder
                .toAddress(emailAddress)
                .content(textContent)
                .htmlContent(htmlContent)
                .build();
    }

    private void populateGroupVars(Group group, Map<String, Object> emailVars) {
        emailVars.put("numberMembers", group.getMembers().size());
        emailVars.put("lengthTime", dateFormat.format(group.getCreatedDateTimeAtSAST()));
        emailVars.put("numberTasks",
                String.valueOf(group.getDescendantEvents().size() + group.getDescendantTodos().size()));
    }

}
