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
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.integration.PdfGeneratingService;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;

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

    private AccountBillingRecordRepository billingRepository;
    private PdfGeneratingService pdfGeneratingService;

    private JavaMailSender mailSender;

    @Autowired
    public EmailSendingBrokerImpl(AccountBillingRecordRepository billingRepository, PdfGeneratingService pdfGeneratingService,
                                  JavaMailSender mailSender) {
        this.billingRepository = billingRepository;
        this.pdfGeneratingService = pdfGeneratingService;
        this.mailSender = mailSender;
    }

    @Override
    public void generateAndSendBillingEmail(String emailSubject, String emailBody, String billingRecordUid) {
        AccountBillingRecord record = billingRepository.findOneByUid(billingRecordUid);

        File invoice = pdfGeneratingService.generateInvoice(billingRecordUid);

        GrassrootEmail email = new GrassrootEmail.EmailBuilder(emailSubject)
                .content(emailBody)
                .address(record.getAccount().getBillingUser().getEmailAddress())
                .attachment("invoice.pdf", invoice)
                .build();

        sendMail(email);
    }

    @Override
    public void sendSystemStatusMail(GrassrootEmail systemStatsEmail) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(emailFromAddress);
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

        MimeMessage mail = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mail, email.hasAttachment());
            helper.setFrom(emailFromAddress, emailFromName);
            helper.setTo(email.getAddress());
            helper.setSubject(email.getSubject());
            helper.setText(email.getContent());
            if (email.hasAttachment()) {
                helper.addAttachment(email.getAttachmentName(), email.getAttachment());
            }
            // mailSender.send(mail);
        } catch (MessagingException|MailException|UnsupportedEncodingException e) {
            logger.warn("Error sending user mail! Exception : " + e.toString());
        }
    }

}
