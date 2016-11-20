package za.org.grassroot.integration;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    // major todo : scheduled job to clean the temp folder
    @Override
    @Transactional(readOnly = true)
    public File generateInvoice(String currentRecordUid) {
        try {
            PdfReader pdfReader = new PdfReader(invoiceTemplatePath);
            File tempStore = File.createTempFile("invoice", "pdf");
            PdfStamper pdfOutput = new PdfStamper(pdfReader, new FileOutputStream(tempStore));
            AcroFields fields = pdfOutput.getAcroFields();

            AccountBillingRecord record = billingRepository.findOneByUid(currentRecordUid);

            Instant priorPeriodStart = record.getBilledPeriodStart().minus(1, ChronoUnit.DAYS); // since the prior statement might have been a day or two earlier
            // todo : replace this with proper logic & iterating through the bills
            List<AccountBillingRecord> priorRecordsInBillingPeriod = billingRepository
                    .findByAccountAndStatementDateTimeBetweenAndCreatedDateTimeBefore(record.getAccount(),
                            priorPeriodStart, record.getBilledPeriodEnd(), record.getCreatedDateTime());

            logger.info("Found these records: " + priorRecordsInBillingPeriod);

            if (priorRecordsInBillingPeriod != null && !priorRecordsInBillingPeriod.isEmpty()) {
                AccountBillingRecord priorRecord = priorRecordsInBillingPeriod.get(0);
                fields.setField("lastBilledAmountDescription", String.format("Last invoice for R%s, dated %s",
                        amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100), formatAtSAST(priorRecord.getCreatedDateTime(), dateHeader)));
                fields.setField("lastBilledAmount", amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100)); // redundancy? with description?
                if (priorRecord.getPaid() && priorRecord.getPaidAmount() != null) {
                    fields.setField("lastPaymentAmountReceived", String.format("Payment received by credit card on %s, " +
                            "thank you", formatAtSAST(priorRecord.getPaidDate(), dateHeader)));
                    fields.setField("lastPaidAmount", amountFormat.format((double) priorRecord.getPaidAmount() / 100));
                }
            }

            fields.setField("invoiceNumber", "INVOICE NO " + record.getId());
            fields.setField("invoiceDate", formatAtSAST(record.getStatementDateTime(), dateHeader));
            fields.setField("billedUserName", record.getAccount().getBillingUser().getDisplayName());
            fields.setField("emailAddress", String.format("Email: %s", record.getAccount().getBillingUser().getEmailAddress()));

            fields.setField("priorBalance", amountFormat.format((double) record.getOpeningBalance() / 100));
            fields.setField("thisBillItems1", String.format("Monthly subscription for '%s' account",
                    record.getAccount().getType().name().toLowerCase()));
            fields.setField("billedAmount1", amountFormat.format((double) record.getAmountBilledThisPeriod() / 100));
            fields.setField("totalAmountToPay", amountFormat.format((double) record.getTotalAmountToPay() / 100));
            fields.setField("footerTextPaymentDate", String.format("The amount due will be automatically charged to " +
                    "your card on %s", formatAtSAST(record.getNextPaymentDate(), dateHeader)));

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
