package za.org.grassroot.integration;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.color.Color;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.GroupRepository;

import javax.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final Locale DEFAULT_LANG = new Locale("en");

    private String folderPath;

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
        this.folderPath = environment.getProperty("grassroot.templates.folder.path");
    }

    @Override
    @Transactional(readOnly = true)
    public File generateInvoice(List<String> billingRecordUids) {
        try {
            PdfReader pdfReader = new PdfReader(folderPath + "/invoice_template.pdf");// Source File

            File tempStorage = File.createTempFile("invoice","pdf");
            tempStorage.deleteOnExit();

            PdfDocument myDocument = new PdfDocument(pdfReader,new PdfWriter(tempStorage.getAbsolutePath()));
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
            return tempStorage;

        } catch (IOException e) {
            logger.warn("Could not find template path! Input: {}", folderPath);
            e.printStackTrace();
            return null;
        }
    }

    @Override

    public File generateGroupFlyer(String groupUid, boolean color, Locale language, String typeOfFile) {//Enum
        // load group entity from group repository using uid
        PdfDocument pdfDocument = null;
        PDDocument pd = null;

        File fileToReturn = null;
        Group grpEntity = groupRepository.findOneByUid(groupUid);

        try {

            fileToReturn = File.createTempFile("flyer","pdf");
            fileToReturn.deleteOnExit();

            String flyerToLoad = folderPath + "/" + chooseFlyerToLoad(color, language);

            // todo: compression (important)
            pdfDocument = new PdfDocument(new PdfReader(flyerToLoad), new PdfWriter(fileToReturn.getAbsolutePath()));

            PdfAcroForm pdfAcroForm = PdfAcroForm.getAcroForm(pdfDocument, true);
            Map<String,PdfFormField> pdfFormFieldMap = pdfAcroForm.getFormFields();

            logger.debug("Fields Map = {}, form field = {}", pdfFormFieldMap, pdfFormFieldMap.get("join_code_header").getValueAsString());

            pdfFormFieldMap.get("group_name").setValue("HOW TO JOIN " + grpEntity.getGroupName().toUpperCase() + " ON GRASSROOT");//**
            pdfFormFieldMap.get("join_code_header").setValue(grpEntity.getGroupTokenCode()).setColor(Color.BLACK);
            pdfFormFieldMap.get("join_code_phone").setValue(grpEntity.getGroupTokenCode()).setColor(Color.BLACK);


            pdfFormFieldMap.get("join_code_header");
            logger.debug("Form Field = {}",pdfFormFieldMap.get("join_code_header").getValueAsString());
            logger.debug("Form Field = {}",pdfFormFieldMap.get("join_code_phone").getValueAsString());

            pdfAcroForm.flattenFields();
            pdfDocument.close();

            if("JPEG".equals(typeOfFile)) {
                pd = PDDocument.load(fileToReturn);
                fileToReturn = generateImage(pd);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // return the pdf as a java File
        return fileToReturn;
    }

    @Override
    public File generateImage(PDDocument pdDocument) throws FileNotFoundException {
        File imageFile = null;
        try{
            PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
            for(int x = 0;x < pdDocument.getNumberOfPages();x++){
                BufferedImage bImage = pdfRenderer.renderImageWithDPI(x,300, ImageType.RGB);
                ImageIOUtil.writeImage(bImage,String.format(folderPath + "/template_image.%s" ,"jpg"),300);
            }
            pdDocument.close();
            imageFile = new File(folderPath + "/template_image.jpg");
        }catch (IOException e){
            e.printStackTrace();
        }
        return  imageFile;
    }

    @Override
    public List<Locale> availableLanguages() {
        if (StringUtils.isEmpty(folderPath)) {
            logger.error("No path for flyers");
            return new ArrayList<>();
        }

        File folder = new File(folderPath);

        if(!folder.isDirectory()) {
            logger.error("Folder path {} does not point to a folder", folderPath);
            return new ArrayList<>();
        }

        File[] filesInFolder = folder.listFiles();
        if (filesInFolder == null) {
            logger.error("No files in template folder");
            return new ArrayList<>();
        }

        logger.debug("files found in template folder = {} files", filesInFolder.length);
        List<String> tempListOfFiles = Arrays.stream(filesInFolder)
                .map(File::getName).filter(n -> n.startsWith("group"))
                .collect(Collectors.toList());

        logger.debug("filtered list of group flyer templates = {}", tempListOfFiles.size());
        return tempListOfFiles.stream()
                .map(name -> {
                    String languageCode = name.split(SEPARATOR)[LANGUAGE_POSITION]; // Check if is a valid name
                    logger.debug("for file name {}, took language separator {}", name, languageCode);
                    return languageCode;
                })
                .filter(lcode -> Arrays.asList(Locale.getISOLanguages()).contains(lcode))
                .map(Locale::new).collect(Collectors.toList());
    }

    @Override
    public String chooseFlyerToLoad(boolean color, Locale language) {
        List<Locale> languages = availableLanguages();
        if (!languages.contains(DEFAULT_LANG)) {
            throw new UnsupportedOperationException("Should not call generate flyer without valid folder");
        }

        File[] filesInFolder = (new File(folderPath)).listFiles();
        if (filesInFolder == null) {
            throw new UnsupportedOperationException("Folder is empty of files");
        }

        final List<String> fileNames = Arrays.stream(filesInFolder).map(File::getName).collect(Collectors.toList());
        final String FLYER_FILE_NAME = "group_join_code_template_%s_%s.pdf";
        final String langCode = languages.contains(language) ? language.getLanguage() : DEFAULT_LANG.getLanguage();
        final String attemptedFile = String.format(FLYER_FILE_NAME, langCode, color ? "colour" : "grey");
        if (!fileNames.contains(attemptedFile)) {
            return String.format(FLYER_FILE_NAME, langCode, color ? "grey" : "colour");
        } else {
            return attemptedFile;
        }
    }
}
