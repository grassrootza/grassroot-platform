package za.org.grassroot.integration.utils;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import za.org.grassroot.integration.email.GrassrootEmail;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * Created by luke on 2017/05/08.
 */
public class EmailUtils {

    public static MimeMessage transformEmailToMail(MimeMessage mail, GrassrootEmail email, String fromAddress) throws
            UnsupportedEncodingException, MessagingException, MailException {
        MimeMessageHelper helper = new MimeMessageHelper(mail, email.hasAttachment() || email.hasHtmlContent());
        helper.setFrom(fromAddress, email.getFrom());
        helper.setSubject(email.getSubject());
        helper.setTo(email.getAddress());

        if (email.hasHtmlContent()) {
            helper.setText(email.getContent(), email.getHtmlContent());
        }  else {
            helper.setText(email.getContent());
        }

        if (email.hasAttachment()) {
            helper.addAttachment(email.getAttachmentName(), email.getAttachment());
        }

        return mail;
    }

}
