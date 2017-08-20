package za.org.grassroot.integration;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.GroupRepository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

    private static final String SEPARATOR = "_";
    private static final int LANGUAGE_POSITION = 4;

    private String invoiceTemplatePath;
    private String folderPath;

    private String flyerPath;
    private String editedFlyerPath;

    private String flyerPath_grey;
    private String editedFlyerPath_grey;

    private AccountBillingRecordRepository billingRepository;
    private Environment environment;
    private GroupRepository groupRepository;

    @Autowired
    public PdfGeneratingServiceImpl(AccountBillingRecordRepository billingRepository, Environment environment,GroupRepository groupRepository) {
        this.billingRepository = billingRepository;
        this.environment = environment;
        this.groupRepository = groupRepository;
    }

    @PostConstruct
    private void init() {
        // todo: remove the properties for the specific files, and get them all from the folder
        this.invoiceTemplatePath = environment.getProperty("grassroot.invoice.template.path", "no_invoice.pdf");
        this.folderPath = environment.getProperty("grassroot.flyer.folder.path");
        this.flyerPath = environment.getProperty("grassroot.flyer.colour.path");
        this.editedFlyerPath = environment.getProperty("grassroot.edited.flyer.colour.path");
        this.flyerPath_grey = environment.getProperty("grassroot.flyer.grey.path");
        this.editedFlyerPath_grey = environment.getProperty("grassroot.edited.flyer.grey.path");
        logger.info("PDF GENERATOR: path = " + invoiceTemplatePath);
        logger.info("PDF GENERATOR: color flyer path = " + flyerPath);
        logger.info("PDF GENERATOR: grey flyer path = " + flyerPath_grey);
    }

    // major todo : switch to Guava temp file handling & clean the temp folder periodically
    @Override
    @Transactional(readOnly = true)
    public File generateInvoice(List<String> billingRecordUids) {
        try {
            PdfReader pdfReader = new PdfReader(invoiceTemplatePath);// Source File

            String destFilePath = "/home/march/grassroot/grassroot-resources/edited_invoice_template.pdf";
            File destinationFile = new File(destFilePath);
            destinationFile.getParentFile().mkdir();

            File tempStore = File.createTempFile("invoice", "pdf");
            tempStore.deleteOnExit();

            PdfDocument myDocument = new PdfDocument(pdfReader,new PdfWriter(destFilePath));
            PdfAcroForm pdfAcroForm = PdfAcroForm.getAcroForm(myDocument,true);

            Map<String,PdfFormField> pdfFormFieldMap = pdfAcroForm.getFormFields();

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

                pdfFormFieldMap.get("lastBilledAmountDescription").setValue(String.format("Last invoice for R%s, dated %s",
                        amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100), formatAtSAST(priorRecord.getCreatedDateTime(), dateHeader)));

                pdfFormFieldMap.get("lastBilledAmount").setValue(amountFormat.format((double) priorRecord.getTotalAmountToPay() / 100));
                if (priorRecord.getPaid() && priorRecord.getPaidAmount() != null) {
                    pdfFormFieldMap.get("lastPaymentAmountReceived").setValue(String.format("Payment received by credit card on %s, " +
                            "thank you", formatAtSAST(priorRecord.getPaidDate(), dateHeader)));
                    pdfFormFieldMap.get("lastPaidAmount").setValue(amountFormat.format((double) priorRecord.getPaidAmount() / 100));
                }
            }

            pdfFormFieldMap.get("invoiceNumber").setValue("INVOICE NO " + latest.getId());
            pdfFormFieldMap.get("invoiceDate").setValue(formatAtSAST(latest.getStatementDateTime(), dateHeader));
            pdfFormFieldMap.get("billedUserName").setValue(latest.getAccount().getBillingUser().getDisplayName());
            pdfFormFieldMap.get("emailAddress").setValue(String.format("Email: %s", account.getBillingUser().getEmailAddress()));
            pdfFormFieldMap.get("priorBalance").setValue(amountFormat.format((double) latest.getOpeningBalance() / 100));
            pdfFormFieldMap.get("thisBillItems1").setValue(String.format("Monthly subscription for '%s' account",
                    latest.getAccount().getType().name().toLowerCase()));

            pdfFormFieldMap.get("billedAmount1").setValue(amountFormat
                    .format((double) latest.getAmountBilledThisPeriod() / 100));

            pdfFormFieldMap.get("totalAmountToPay").setValue(amountFormat
                    .format((double) latest.getTotalAmountToPay() / 100));

            // todo: reintroduce these fields if needed
            //pdfFormFieldMap.get("footerTextPaymentDate").setValue(String.format("The amount due will be automatically charged to " +
                    //"your card on %s", formatAtSAST(latest.getNextPaymentDate(), dateHeader)));
            //fields.setField("footerTextPaymentDate", String.format("The amount due will be automatically charged to " +
                    //"your card on %s", formatAtSAST(latest.getNextPaymentDate(), dateHeader)));

            pdfAcroForm.flattenFields();
            myDocument.close();
            pdfReader.close();

            logger.info("Invoice PDF generated, returning ... ");
            return destinationFile;

        } catch (IOException e) {
            logger.warn("Could not find template path! Input: {}", invoiceTemplatePath);
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public File generateGroupFlyer(String groupUid, boolean color, Locale language) {
        PdfDocument pdfdocument;
        File fileToReturn = null;
        Group grpEntity = groupRepository.findOneByUid(groupUid);

        try {

            String flyerPathToLoad = color ? flyerPath : flyerPath_grey;
            String flyerPathToWrite = color ? editedFlyerPath : editedFlyerPath_grey;
            fileToReturn = new File(flyerPathToWrite);
            fileToReturn.getParentFile().mkdir();
            pdfdocument = new PdfDocument(new PdfReader(flyerPathToLoad), new PdfWriter(flyerPathToWrite));

            PdfAcroForm pdfAcroForm = PdfAcroForm.getAcroForm(pdfdocument,true);
            Map<String,PdfFormField> pdfFormFieldMap = pdfAcroForm.getFormFields();

            logger.debug("Fields Map = {}",pdfFormFieldMap);

            pdfFormFieldMap.get("group_name").setValue(grpEntity.getGroupName());
            pdfFormFieldMap.get("join_code_header").setValue(grpEntity.getGroupTokenCode());
            pdfFormFieldMap.get("join_code_phone").setValue(grpEntity.getGroupTokenCode());

            pdfAcroForm.flattenFields();

            // todo: make sure these are set
            //pdfOutput.setFullCompression();

            pdfdocument.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // return the pdf as a java File
        return fileToReturn;
    }
    @Override
    public List<Locale> availableLanguages() {
        List<Locale> languages = new ArrayList<>();
        File folder = new File(folderPath);

        if(!folder.isDirectory()) {
            // todo: throw this exception
            //Throw exception
        } else{
            File[] filesInFolder = folder.listFiles();

            // todo: check that filesInFOlder is not null
            String[] names = new String[filesInFolder.length];

            for(int x = 0;x < filesInFolder.length; x++) {
                names[x] = filesInFolder[x].getName();
            }

            for(int x = 0;x < names.length;x++) {
                String[] data = names[x].split(SEPARATOR);
                String name = data[LANGUAGE_POSITION];// Check if is a valid name

                if (Arrays.asList(Locale.getISOLanguages()).contains(name)) {
                    Locale lang = new Locale(name);
                    languages.add(lang);
                }
            }
        }
        logger.debug("Languages = {}",languages);
        return languages;
    }
}
