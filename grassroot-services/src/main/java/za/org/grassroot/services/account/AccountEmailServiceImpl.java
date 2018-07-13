package za.org.grassroot.services.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.core.dto.GrassrootEmail;

import java.util.Locale;

/**
 * Created by luke on 2017/03/01.
 */
@Service @Slf4j
@ConditionalOnProperty("spring.thymeleaf.enabled")
public class AccountEmailServiceImpl implements AccountEmailService {

    private final TemplateEngine templateEngine;

    @Autowired
    public AccountEmailServiceImpl(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public GrassrootEmail generateDonationShareEmail(String fromName, String toAddress, String linkToDonate) {
        final String subject = fromName + " thinks you should donate to Grassroot";
        final Context ctx = new Context(Locale.getDefault());
        ctx.setVariable("fromName", fromName);
        ctx.setVariable("shareLink", linkToDonate);
        final String htmlContent = templateEngine.process("html/donate_share_email", ctx);
        log.debug("processed template ... firing off mail, content: {}", htmlContent);
        return new GrassrootEmail
                .EmailBuilder(subject)
                .toAddress(toAddress)
                .fromName("Grassroot")
                .htmlContent(htmlContent)
                .content(htmlContent)
                .build();
    }

}
