package za.org.grassroot.integration.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Created by luke on 2016/10/24.
 */
@Service
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class EmailSendingBrokerImpl implements EmailSendingBroker {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendingBrokerImpl.class);

    @Value("${grassroot.mail.from}")
    String emailFrom;

    @Value("${grassroot.system.mail}")
    String systemEmailAddress;

    private JavaMailSender mailSender;

    @Autowired
    public EmailSendingBrokerImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendSystemStatusMail(GrassrootEmail systemStatsEmail) {
        logger.info("Sending grassroot email, which will be from : " + emailFrom);
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(emailFrom);
        mail.setTo(systemEmailAddress);
        mail.setSubject(systemStatsEmail.getSubject());
        mail.setText(systemStatsEmail.getContent());
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            logger.warn("Error sending system mail! Exception : " + e.toString());
        }
    }

    @Override
    public void sendMail(GrassrootEmail email) {
        logger.info("about to send");
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(emailFrom);
        mail.setTo(email.getAddress());
        mail.setSubject(email.getSubject());
        mail.setText(email.getContent());
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            logger.warn("Error sending user mail! Exception : " + e.toString());
        }
    }

}
