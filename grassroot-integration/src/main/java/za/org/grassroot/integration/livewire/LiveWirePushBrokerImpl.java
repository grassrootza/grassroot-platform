package za.org.grassroot.integration.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.repository.LiveWireAlertRepository;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.integration.utils.EmailUtils;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by luke on 2017/05/08.
 */
@Service
public class LiveWirePushBrokerImpl implements LiveWirePushBroker {

    private static final Logger logger = LoggerFactory.getLogger(LiveWirePushBrokerImpl.class);

    @Value("${grassroot.livewire.from.address:livewire@grassroot.org.za}")
    private String livewireEmailAddress;

    @Value("${grassroot.livewire.email.pwd:12345}")
    private String liveWireEmailPassword;

    private JavaMailSender mailSender;
    private final Environment environment;
    private final LiveWireAlertRepository liveWireAlertRepository;

    @Autowired
    public LiveWirePushBrokerImpl(Environment environment, LiveWireAlertRepository liveWireAlertRepository) {
        this.environment = environment;
        this.liveWireAlertRepository = liveWireAlertRepository;
    }

    @PostConstruct
    public void init() {
        JavaMailSenderImpl mailSenderConfig = new JavaMailSenderImpl();
        mailSenderConfig.setHost(environment.getProperty("spring.mail.host"));
        mailSenderConfig.setPort(environment.getProperty("spring.mail.port", Integer.class));
        mailSenderConfig.setUsername(livewireEmailAddress);
        mailSenderConfig.setPassword(liveWireEmailPassword);

        Properties props = mailSenderConfig.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // props.put("mail.debug", "true");

        logger.debug("LiveWire EMAIL: set username as: {}", mailSenderConfig.getUsername());

        this.mailSender = mailSenderConfig;
    }

    @Async
    @Override
    @Transactional
    public void sendLiveWireEmails(String alertUid, List<GrassrootEmail> emails) {
        MimeMessage[] messages = new MimeMessage[emails.size()];
        LiveWireAlert alert = liveWireAlertRepository.findOneByUid(alertUid);
        List<String> emailAddresses = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            try {
                MimeMessage mail = mailSender.createMimeMessage();
                messages[i] = EmailUtils.transformEmailToMail(mail, emails.get(i), livewireEmailAddress);
                emailAddresses.add(emails.get(i).getAddress());
            } catch (UnsupportedEncodingException|MessagingException e) {
                logger.warn("Error encoding or creating one of the emails");
            }
        }
        try {
            logger.info("Sending LiveWire alert of dest type {}, to emails: {}",
                    alert.getDestinationType(), emailAddresses);
            if (!environment.acceptsProfiles("localpg")) {
                mailSender.send(messages);
            }
            alert.setSent(true);
        } catch (MailException|NoSuchMessageException e) {
            logger.info("Error sending mails!: {}", e.toString());
            alert.setSent(false);
        }
    }
}