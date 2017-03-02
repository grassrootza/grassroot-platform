package za.org.grassroot.services.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.integration.email.GrassrootEmail;

import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.formatAtSAST;
import static za.org.grassroot.services.util.MessageUtils.getUserLocale;
import static za.org.grassroot.services.util.MessageUtils.shortDateFormatter;

/**
 * Created by luke on 2017/03/01.
 */
@Service
public class AccountEmailServiceImpl implements AccountEmailService {

    private final MessageSourceAccessor messageSourceAccessor;
    private final TemplateEngine templateEngine;

    @Value("${grassroot.sponsorship.response.url:http://localhost:8080/account/sponsor/respond}")
    private String sponsorshipResponseUrl;

    @Value("${grassroot.sponsorship.request.url:http://localhost:8080/account/sponsor/request}")
    private String urlForRequest;

    private static final DecimalFormat billFormat = new DecimalFormat("#.##");

    @Autowired
    public AccountEmailServiceImpl(@Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor,
                                   @Qualifier("emailTemplateEngine") TemplateEngine templateEngine) {
        this.messageSourceAccessor = messageSourceAccessor;
        this.templateEngine = templateEngine;
    }

    @Override
    public String createAccountBillingNotification(AccountBillingRecord record) {
        return messageSourceAccessor.getMessage("sms.statement.notification", new String[] {
                billFormat.format((double) record.getTotalAmountToPay() / 100), formatAtSAST(record.getNextPaymentDate(), shortDateFormatter)
        }, getUserLocale(record.getAccount().getBillingUser()));
    }

    @Override
    public String createAccountStatementSubject(AccountBillingRecord generatingRecord) {
        return messageSourceAccessor.getMessage("email.statement.subject", getUserLocale(generatingRecord.getAccount().getBillingUser()));
    }

    @Override
    public String createAccountStatementEmail(AccountBillingRecord generatingRecord) {
        final User billedUser = generatingRecord.getAccount().getBillingUser();
        final String salutation = messageSourceAccessor.getMessage("email.statement.salutation",
                new String[] { StringUtils.isEmpty(billedUser.getFirstName()) ? billedUser.getFirstName() : billedUser.nameToDisplay() },
                getUserLocale(billedUser));

        if (generatingRecord.getNextPaymentDate() == null) {
            throw new IllegalArgumentException("Error! Statement emails can only be generated for bill requiring payment");
        }

        final String body = messageSourceAccessor.getMessage("email.statement.body",
                new String[] { billFormat.format((double) generatingRecord.getTotalAmountToPay() / 100),
                        formatAtSAST(generatingRecord.getNextPaymentDate(), shortDateFormatter) }, getUserLocale(billedUser));

        final String closing = messageSourceAccessor.getMessage("email.statement.closing", getUserLocale(billedUser));

        return String.join("\n\n", Arrays.asList(salutation, body, closing));
    }

    @Override
    public String createEndOfTrialNotification(Account account) {
        return messageSourceAccessor.getMessage("notification.account.trial.ended");
    }

    @Override
    public String createEndOfTrialEmailSubject() {
        return messageSourceAccessor.getMessage("email.account.trial.ended.subject");
    }

    @Override
    public String createEndOfTrialEmailBody(Account account, User adminToEmail, String paymentLink) {
        String[] emailFields = new String[] {
                adminToEmail.getName(),
                paymentLink
        };

        return messageSourceAccessor.getMessage("email.account.trial.ended.body", emailFields);
    }

    @Override
    public String createDisabledNotification(Account account) {
        return messageSourceAccessor.getMessage("notification.account.disabled");
    }

    @Override
    public String createDisabledEmailSubject() {
        return messageSourceAccessor.getMessage("email.account.disabled.subject");
    }

    @Override
    public String createDisabledEmailBody(User adminToEmail, String paymentLink) {
        return messageSourceAccessor.getMessage("email.account.disabled.subject");
    }

    @Override
    public GrassrootEmail createSponsorshipRequestMail(AccountSponsorshipRequest request, User requestingUser, String messageFromUser, boolean isReminder) {
        final Account account = request.getRequestor();
        final String subjectKey = isReminder ? "email.sponsorship.reminder.subject" : "email.sponsorship.subject";
        final String bodyKey = isReminder ? "email.sponsorship.reminder.body" : "email.sponsorship.request";

        final String requestLink = sponsorshipResponseUrl + "?requestUid=" + request.getUid();
        final String subject = messageSourceAccessor.getMessage(subjectKey, new String[]{account.getName()});
        final String amount = "R" + (new DecimalFormat("#,###.##").format(account.calculatePeriodCost() / 100));
        final String body = messageSourceAccessor.getMessage(bodyKey, new String[]{request.getDestination().getName(),
                account.getName(), amount, requestLink});
        final String message = messageSourceAccessor.getMessage("email.sponsorship.message",
                new String[]{requestingUser.getName(), messageFromUser});
        final String ending = messageSourceAccessor.getMessage("email.sponsorship.ending");

        final Context ctx = new Context();
        try {
            final String htmlContent = templateEngine.process("html/sponsor_request", ctx);
            GrassrootEmail.EmailBuilder builder = new GrassrootEmail.EmailBuilder(subject)
                    .address(request.getDestination().getEmailAddress())
                    .content(body + message + ending)
                    .htmlContent(htmlContent);
            return builder.build();
        } catch (TemplateInputException e) {
            e.printStackTrace();
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            URL[] urls = ((URLClassLoader)cl).getURLs();
            for(URL url: urls){
                System.out.println(url.getFile());
            }
            throw e;
        }
    }

    @Override
    public GrassrootEmail openingUserEmail(boolean alreadyOpen, final String requestLink, final String destinationName, final User openingUser) {
        final String subject = messageSourceAccessor.getMessage("email.sponsorship.requested.subject");
        final String body = messageSourceAccessor.getMessage(alreadyOpen ? "email.sponsorship.reminded.body" : "email.sponsorship.requested.body",
                new String[] { openingUser.getName(), destinationName, requestLink});

        return new GrassrootEmail.EmailBuilder(subject)
                .address(openingUser.getEmailAddress())
                .content(body)
                .build();
    }

    @Override
    public GrassrootEmail sponsorshipDeniedEmail(AccountSponsorshipRequest request) {
        final String subject = messageSourceAccessor.getMessage("email.sponsorship.denied.subject");
        // note : since the sponsorship was not approved, the billing user will still be the one that opened the account
        final String[] fields = { request.getRequestor().getBillingUser().getName(), request.getDestination().getName(),
                urlForRequest + "?accountUid=" + request.getRequestor().getUid() };

        // todo : should not be billing user ... (or, should be all admin)
        return new GrassrootEmail.EmailBuilder(subject)
                .address(request.getRequestor().getBillingUser().getEmailAddress())
                .content(messageSourceAccessor.getMessage("email.sponsorship.denied.body", fields))
                .build();
    }

    @Override
    public GrassrootEmail sponsorshipApprovedEmail(AccountSponsorshipRequest request) {
        final String subject = messageSourceAccessor.getMessage("email.sponsorship.approved.subject");
        final String[] fields = new String[2];
        // fields[1] = "https://app.grassroot.org.za/send a thank you";
        List<String> addresses = request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()) && !u.equals(request.getDestination()))
                .map(User::getEmailAddress).collect(Collectors.toList());
        return new GrassrootEmail.EmailBuilder(subject)
                .address(String.join(",", addresses))
                .content(messageSourceAccessor.getMessage("email.sponsorship.approved.body", fields))
                .build();
    }

    @Override
    public GrassrootEmail sponsorshipReminderEmailSponsor(AccountSponsorshipRequest request) {
        final String subject = messageSourceAccessor.getMessage("email.sponsorship.aborted.subject");
        final String fieldsDest[] = { request.getDestination().getName(), request.getRequestor().getName(),
                sponsorshipResponseUrl + "?requestUid=" + request.getUid() };

        return new GrassrootEmail.EmailBuilder(subject)
                .address(request.getDestination().getEmailAddress())
                .content(messageSourceAccessor.getMessage("email.sponsorship.aborted.body.dest", fieldsDest))
                .build();
    }

    // consider replacing with just a single email to multiple emails (as with approved email, above)
    @Override
    public Set<GrassrootEmail> sponsorshipReminderEmailRequestor(AccountSponsorshipRequest request) {
        Set<GrassrootEmail> emailSet = new HashSet<>();

        final String subject = messageSourceAccessor.getMessage("email.sponsorship.aborted.subject");
        final String fieldsReq[] = new String[3];
        fieldsReq[1] = request.getDestination().getName();

        request.getRequestor().getAdministrators()
                .stream()
                .filter(u -> !StringUtils.isEmpty(u.getEmailAddress()))
                .forEach(u -> {
                    fieldsReq[0] = u.getName();
                    emailSet.add(new GrassrootEmail.EmailBuilder(subject)
                            .address(u.getEmailAddress())
                            .content(messageSourceAccessor.getMessage("email.sponsorship.aborted.body.req", fieldsReq))
                            .build());
                });

        return emailSet;
    }

}
