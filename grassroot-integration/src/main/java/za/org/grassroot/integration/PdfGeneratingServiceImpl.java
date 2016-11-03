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

/**
 * Created by luke on 2016/11/03.
 */
@Service
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class PdfGeneratingServiceImpl implements PdfGeneratingService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratingServiceImpl.class);

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
    public File generateInvoice(String billingRecordUid) {
        try {
            PdfReader pdfReader = new PdfReader(invoiceTemplatePath);
            File tempStore = File.createTempFile("invoice", "pdf");
            PdfStamper pdfOutput = new PdfStamper(pdfReader, new FileOutputStream(tempStore));
            AcroFields fields = pdfOutput.getAcroFields();

            AccountBillingRecord record = billingRepository.findOneByUid(billingRecordUid);

            fields.setField("BilledUserName", record.getAccount().getBillingUser().getDisplayName());
            fields.setField("EmailAddress", record.getAccount().getBillingUser().getEmailAddress());

            pdfOutput.setFormFlattening(true);
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
