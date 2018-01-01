package za.org.grassroot.services.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.services.util.MessageUtils;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static za.org.grassroot.core.util.DateTimeUtil.formatAtSAST;
import static za.org.grassroot.services.util.MessageUtils.getUserLocale;
import static za.org.grassroot.services.util.MessageUtils.shortDateFormatter;

/**
 * Created by luke on 2017/03/01.
 */
@Service
public class AccountEmailServiceImpl implements AccountEmailService {

    private final MessageSourceAccessor messageSource;
    private final TemplateEngine templateEngine;

    @Value("${grassroot.sponsorship.response.url:http://localhost:8080/account/sponsor/respond}")
    private String sponsorshipResponseUrl;

    @Value("${grassroot.sponsorship.request.url:http://localhost:8080/account/sponsor/request}")
    private String urlForRequest;

    @Value("${grassroot.account.view.url:http://localhost:8080/account/view?requestUid=}")
    private String urlToViewAccount;

    private static final DecimalFormat billFormat = new DecimalFormat("#.##");

    @Autowired
    public AccountEmailServiceImpl(@Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor,
                                   @Qualifier("emailTemplateEngine") TemplateEngine templateEngine) {
        this.messageSource = messageSourceAccessor;
        this.templateEngine = templateEngine;
    }

    @Override
    public String createAccountBillingNotification(AccountBillingRecord record) {
        Instant nextPayDate = record.getNextPaymentDate() == null ? Instant.now().plus(7, ChronoUnit.DAYS) : record.getNextPaymentDate();
        return messageSource.getMessage("sms.statement.notification", new String[] {
                billFormat.format((double) record.getTotalAmountToPay() / 100), formatAtSAST(nextPayDate, shortDateFormatter)
        }, getUserLocale(record.getAccount().getBillingUser()));
    }

    @Override
    @Transactional(readOnly = true)
    public GrassrootEmail createAccountStatementEmail(AccountBillingRecord statement) {
        Objects.requireNonNull(statement);

        final User billedUser = statement.getAccount().getBillingUser();
        final String subject = messageSource.getMessage("email.statement.subject");
        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder(subject);

        if (statement.getNextPaymentDate() == null) {
            throw new IllegalArgumentException("Error! Statement emails can only be generated for bill requiring payment");
        }

        final Context ctx = new Context(getUserLocale(billedUser));
        ctx.setVariable("toName", StringUtils.isEmpty(billedUser.getFirstName()) ? billedUser.getFirstName() : billedUser.nameToDisplay());
        ctx.setVariable("amountToPay", billFormat.format((double) statement.getTotalAmountToPay() / 100));
        ctx.setVariable("nextPaymentDate", formatAtSAST(statement.getNextPaymentDate(), shortDateFormatter));
        ctx.setVariable("viewAccountUrl", urlToViewAccount + statement.getAccount().getUid());

        final String template = "account_statement";
        return builder.address(billedUser.getEmailAddress())
                .content(templateEngine.process("text/" + template, ctx))
                .htmlContent(templateEngine.process("html/" + template, ctx))
                .build();
    }

    @Override
    public String createEndOfTrialNotification(Account account) {
        return messageSource.getMessage("notification.account.trial.ended");
    }

    @Override
    public GrassrootEmail createEndOfTrailEmail(Account account, User adminToEmail, String paymentLink) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(adminToEmail);
        Objects.requireNonNull(paymentLink);

        // todo : have multiple links/types (e.g., with direct deposit) [and also campaign tracking etc]

        final String template = "trial_ended";
        final Context ctx = new Context(MessageUtils.getUserLocale(adminToEmail));
        ctx.setVariable("toName", adminToEmail.getName());
        ctx.setVariable("paymentLink", paymentLink);

        return new GrassrootEmail.EmailBuilder(messageSource.getMessage("email.account.trial.ended.subject"))
                .address(adminToEmail.getEmailAddress())
                .content(templateEngine.process("text/" + template, ctx))
                .htmlContent(templateEngine.process("html/" + template, ctx))
                .build();
    }

    @Override
    public String createDisabledNotification(Account account) {
        return messageSource.getMessage("notification.account.disabled");
    }

    @Override
    public GrassrootEmail createDisabledEmail(User adminToEmail, String paymentLink) {
        Objects.requireNonNull(adminToEmail);
        Objects.requireNonNull(paymentLink);

        final String template = "account_disabled";
        final Context ctx = new Context(MessageUtils.getUserLocale(adminToEmail));

        ctx.setVariable("toName", adminToEmail.getName());
        ctx.setVariable("paymentLink", paymentLink);

        return new GrassrootEmail.EmailBuilder(messageSource.getMessage("email.account.disabled.subject"))
                .address(adminToEmail.getEmailAddress())
                .content(templateEngine.process("text/" + template, ctx))
                .htmlContent(templateEngine.process("html/" + template, ctx))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrassrootEmail createSponsorshipRequestMail(AccountSponsorshipRequest request, User requestingUser, String messageFromUser, boolean isReminder) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(requestingUser);

        final Account account = request.getRequestor();
        final String subjectKey = isReminder ? "email.sponsorship.reminder.subject" : "email.sponsorship.subject";

        final String requestLink = sponsorshipResponseUrl + "?requestUid=" + request.getUid();
        final String subject = messageSource.getMessage(subjectKey, new String[]{account.getName()});
        final String amount = "R" + (new DecimalFormat("#,###.##").format(account.calculatePeriodCost() / 100));

        final Context ctx = new Context(MessageUtils.getUserLocale(request.getDestination()));
        ctx.setVariable("toName", request.getDestination().getName());
        ctx.setVariable("fromName", requestingUser.getName());
        ctx.setVariable("requestLink", requestLink);
        ctx.setVariable("userMessage", messageFromUser);
        ctx.setVariable("amountToPay", amount);

        final String templateSuffix = isReminder ? "sponsor_reminder" : "sponsor_request";
        final String textContent = templateEngine.process("text/" + templateSuffix, ctx);
        final String htmlContent = templateEngine.process("html/" + templateSuffix, ctx);

        GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder(subject)
                .address(request.getDestination().getEmailAddress())
                .content(textContent)
                .htmlContent(htmlContent);
        return builder.build();
    }

    @Override
    public GrassrootEmail openingUserEmail(boolean alreadyOpen, final String requestLink, final String destinationName, final User openingUser) {
        final Context ctx = new Context(MessageUtils.getUserLocale(openingUser));
        ctx.setVariable("requestorName", openingUser.getName());
        ctx.setVariable("destinationName", destinationName);
        ctx.setVariable("requestLink", requestLink);

        final String templateSuffix = alreadyOpen ? "sponsor_request_opened" : "sponsor_reminder_sent";
        final String subject = messageSource.getMessage("email.sponsorship.requested.subject");
        final String body = templateEngine.process("text/" + templateSuffix, ctx);
        final String htmlBody = templateEngine.process("html/" + templateSuffix, ctx);

        return new GrassrootEmail.EmailBuilder(subject)
                .address(openingUser.getEmailAddress())
                .content(body)
                .htmlContent(htmlBody)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrassrootEmail sponsorshipDeniedEmail(AccountSponsorshipRequest request) {
        final String subject = messageSource.getMessage("email.sponsorship.denied.subject");
        // note : since the sponsorship was not approved, the billing user will still be the one that opened the account

        final Context ctx = new Context(MessageUtils.getUserLocale(request.getRequestor().getBillingUser()));
        ctx.setVariable("request", request);
        ctx.setVariable("urlForNewRequest", urlForRequest + "?accountUid=" + request.getRequestor().getUid());

        // todo : should not be billing user ... (or, should be all admin)
        return new GrassrootEmail.EmailBuilder(subject)
                .address(request.getRequestor().getBillingUser().getEmailAddress())
                .content(templateEngine.process("text/sponsorship_denied", ctx))
                .htmlContent(templateEngine.process("html/sponsorship_denied", ctx))
                .build();
    }

    @Override
    public GrassrootEmail sponsorshipApprovedEmail(AccountSponsorshipRequest request) {
        final String subject = messageSource.getMessage("email.sponsorship.approved.subject");

        final Context ctx = new Context();
        ctx.setVariable("accountLink", urlToViewAccount + request.getRequestor().getUid());

        return new GrassrootEmail.EmailBuilder(subject)
                .content(templateEngine.process("text/sponsorship_approved", ctx))
                .htmlContent(templateEngine.process("html/sponsorship_approved", ctx))
                .build();
    }

    @Override
    public GrassrootEmail sponsorshipReminderEmailSponsor(AccountSponsorshipRequest request) {
        final String subject = messageSource.getMessage("email.sponsorship.aborted.subject");
        final Context ctx = new Context(MessageUtils.getUserLocale(request.getDestination()));
        ctx.setVariable("toName", request.getDestination().getName());
        ctx.setVariable("fromName", request.getRequestor().getName());
        ctx.setVariable("requestLink", sponsorshipResponseUrl + "?requestUid=" + request.getUid());

        return new GrassrootEmail.EmailBuilder(subject)
                .address(request.getDestination().getEmailAddress())
                .content(templateEngine.process("text/sponsor_auto_reminder", ctx))
                .htmlContent(templateEngine.process("html/sponsor_auto_reminder", ctx))
                .build();
    }

    // consider replacing with just a single email to multiple emails (as with approved email, above)
    @Override
    public Set<GrassrootEmail> sponsorshipReminderEmailRequestor(AccountSponsorshipRequest request) {
        Set<GrassrootEmail> emailSet = new HashSet<>();

        final String subject = messageSource.getMessage("email.sponsorship.aborted.subject");
        final Context ctx = new Context();
        ctx.setVariable("destinationName", request.getDestination().getName());
        ctx.setVariable("requestLink", sponsorshipResponseUrl + "?requestUid=" + request.getUid());

        request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()))
                .forEach(u -> {
                    ctx.setVariable("toName", u.getName());
                    emailSet.add(new GrassrootEmail.EmailBuilder(subject)
                            .address(u.getEmailAddress())
                            .content(templateEngine.process("text/sponsor_auto_reminder_requestor", ctx))
                            .htmlContent(templateEngine.process("html/sponsor_auto_reminder_requestor", ctx))
                            .build());
                });

        return emailSet;
    }

}
