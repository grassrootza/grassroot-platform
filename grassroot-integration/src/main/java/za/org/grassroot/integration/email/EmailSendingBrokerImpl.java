package za.org.grassroot.integration.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.org.grassroot.integration.PdfGeneratingService;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by luke on 2016/10/24.
 */
@Service
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class EmailSendingBrokerImpl implements EmailSendingBroker {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendingBrokerImpl.class);

    @Value("${grassroot.mail.from.address}")
    private String emailFromAddress;

    @Value("${grassroot.mail.from.name}")
    private String emailFromName;

    @Value("${grassroot.system.mail}")
    private String systemEmailAddress;

    private final PdfGeneratingService pdfGeneratingService;
    private final JavaMailSender mailSender;

    @Autowired
    public EmailSendingBrokerImpl(PdfGeneratingService pdfGeneratingService, JavaMailSender mailSender) {
        this.pdfGeneratingService = pdfGeneratingService;
        this.mailSender = mailSender;
    }

    @Override
    public void generateAndSendStatementEmail(GrassrootEmail baseMail, List<String> billingRecordsToIncludeByUid) {
        File invoice = pdfGeneratingService.generateInvoice(billingRecordsToIncludeByUid);
        String fileName = "GrassrootInvoice-" + DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now()) + ".pdf";
        baseMail.setAttachment(invoice);
        baseMail.setAttachmentName(fileName);
        sendMail(baseMail);
    }

    @Override
    public void sendSystemStatusMail(GrassrootEmail systemStatsEmail) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(emailFromAddress);

        if (systemEmailAddress.contains(",")) {
            mail.setTo(systemEmailAddress.split(","));
        } else {
            mail.setTo(systemEmailAddress);
        }
        mail.setSubject(systemStatsEmail.getSubject());
        mail.setText(systemStatsEmail.getContent());

        try {
            logger.info("Sending system email to: {}", systemEmailAddress);
            mailSender.send(mail);
        } catch (MailException e) {
            logger.warn("Error sending system mail! Exception : " + e.toString());
        }
    }

    @Async
    @Override
    public void sendMail(GrassrootEmail email) {
        MimeMessage mail = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mail, email.hasAttachment() || email.hasHtmlContent());
            helper.setFrom(emailFromAddress, emailFromName);
            helper.setTo(email.getAddress());
            helper.setSubject(email.getSubject());

            if (email.hasHtmlContent()) {
                helper.setText(email.getContent(), email.getHtmlContent());
            }  else {
                helper.setText(email.getContent());
            }

            if (email.hasAttachment()) {
                helper.addAttachment(email.getAttachmentName(), email.getAttachment());
            }
            logger.info("Okay, sending a mail, to : " + email.getAddress());
            mailSender.send(mail);
        } catch (MessagingException|MailException|UnsupportedEncodingException e) {
            logger.warn("Error sending user mail! Exception : " + e.toString());
        }
    }

}
