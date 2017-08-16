package za.org.grassroot.integration;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static za.org.grassroot.core.specifications.BillingSpecifications.*;
import static za.org.grassroot.core.util.DateTimeUtil.formatAtSAST;

/**
 * Created by luke on 2016/11/03.
 */
@Service
public class PdfGeneratingServiceImpl implements PdfGeneratingService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratingServiceImpl.class);

    private static final DateTimeFormatter dateHeader = DateTimeFormatter.ofPattern("d MMM YYYY");
    private static final DateTimeFormatter paymentDateTimeFormat = DateTimeFormatter.ofPattern("HH:mm, d MMM");
    private static final DecimalFormat amountFormat = new DecimalFormat("#.00");

    private String invoiceTemplatePath;

    private AccountBillingRecordRepository billingRepository;
    private Environment environment;

    @Autowired
    public PdfGeneratingServiceImpl(AccountBillingRecordRepository billingRepository, Environment environment) {
        this.billingRepository = billingRepository;
        this.environment = environment;
    }

    @PostConstruct
    private void init() {
        this.invoiceTemplatePath = environment.getProperty("grassroot.invoice.template.path", "no_invoice.pdf");
        logger.info("PDF GENERATOR: path = " + invoiceTemplatePath);
    }

    // major todo : switch to Guava temp handling & clean the temp folder periodically
    @Override
    @Transactional(readOnly = true)
    public File generateInvoice(List<String> billingRecordUids) {
        try {
            PdfReader pdfReader = new PdfReader(invoiceTemplatePath);
            File tempStore = File.createTempFile("invoice", "pdf");
            tempStore.deleteOnExit();

            PdfStamper pdfOutput = new PdfStamper(pdfReader, new FileOutputStream(tempStore));
            AcroFields fields = pdfOutput.getAcroFields();

            List<AccountBillingRecord> records = billingRepository.findByUidIn(billingRecordUids);
            records.sort(Comparator.reverseOrder());

            AccountBillingRecord latest = records.get(0);
            AccountBillingRecord earliest = records.get(records.size() - 1);

            Account account = latest.getAccount();
            Instant priorPeriodStart = earliest.getBilledPeriodStart().minus(1, ChronoUnit.DAYS); // since the prior statement might have been a day or two earlier

            if (latest.getStatementDateTime() == null) {
                throw new IllegalArgumentException("Errror! Latest bill must be a statement generating record");
            }

            List<AccountBillingRecord> priorPaidBills = billingRepository.findAll(Specifications.where(forAccount(account))
                    .and(createdBetween(priorPeriodStart, earliest.getBilledPeriodStart(), false))
                    .and(isPaid(true)));

            if (priorPaidBills != null && !priorPaidBills.isEmpty()) {
                priorPaidBills.sort(Comparator.reverseOrder());
                AccountBillingRecord priorRecord = priorPaidBills.get(0);
                fields.setField("lastBilledAmountDescription", String.format("Last invoice for R%s, dated %s",
                        amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100), formatAtSAST(priorRecord.getCreatedDateTime(), dateHeader)));
                fields.setField("lastBilledAmount", amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100)); // redundancy? with description?
                if (priorRecord.getPaid() && priorRecord.getPaidAmount() != null) {
                    fields.setField("lastPaymentAmountReceived", String.format("Payment received by credit card on %s, " +
                            "thank you", formatAtSAST(priorRecord.getPaidDate(), dateHeader)));
                    fields.setField("lastPaidAmount", amountFormat.format((double) priorRecord.getPaidAmount() / 100));
                }
            }

            fields.setField("invoiceNumber", "INVOICE NO " + latest.getId());
            fields.setField("invoiceDate", formatAtSAST(latest.getStatementDateTime(), dateHeader));
            fields.setField("billedUserName", latest.getAccount().getBillingUser().getDisplayName());
            fields.setField("emailAddress", String.format("Email: %s", account.getBillingUser().getEmailAddress()));

            fields.setField("priorBalance", amountFormat.format((double) latest.getOpeningBalance() / 100));
            fields.setField("thisBillItems1", String.format("Monthly subscription for '%s' account",
                    latest.getAccount().getType().name().toLowerCase()));
            fields.setField("billedAmount1", amountFormat.format((double) latest.getAmountBilledThisPeriod() / 100));
            fields.setField("totalAmountToPay", amountFormat.format((double) latest.getTotalAmountToPay() / 100));
            fields.setField("footerTextPaymentDate", String.format("The amount due will be automatically charged to " +
                    "your card on %s", formatAtSAST(latest.getNextPaymentDate(), dateHeader)));

            pdfOutput.setFormFlattening(true);
            pdfOutput.setFullCompression();

            pdfOutput.close();
            pdfReader.close();

            logger.info("Invoice PDF generated, returning ... ");

            return tempStore;

        } catch (IOException e) {
            logger.warn("Could not find template path! Input: {}", invoiceTemplatePath);
            e.printStackTrace();
            return null;
        } catch (DocumentException e) {
            logger.warn("Error! Could not write PDF invoice document");
            e.printStackTrace();
            return null;
        }
    }
}
