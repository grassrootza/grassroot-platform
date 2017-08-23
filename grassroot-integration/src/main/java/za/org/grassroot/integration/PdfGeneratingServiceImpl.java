package za.org.grassroot.integration;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.color.Color;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.PdfWriter;
/*import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfStamper;*/
//import com.itextpdf.text.pdf.PdfWriter;
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
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.GroupRepository;

import javax.annotation.PostConstruct;

import java.awt.image.BufferedImage;
import java.io.File;
//impo import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

    private static final String SEPARATOR = "_";
    private static final int LANGUAGE_POSITION = 4;

    private String invoiceTemplatePath;
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
        this.invoiceTemplatePath = environment.getProperty("grassroot.invoice.template.path", "no_invoice.pdf");
        this.folderPath = environment.getProperty("grassroot.flyer.folder.path");

        logger.info("PDF GENERATOR: path = " + invoiceTemplatePath);
    }

    // major todo : switch to Guava temp handling & clean the temp folder periodically
    @Override
    @Transactional(readOnly = true)
    public File generateInvoice(List<String> billingRecordUids) {
        try {
            PdfReader pdfReader = new PdfReader(invoiceTemplatePath);// Source File

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
            logger.warn("Could not find template path! Input: {}", invoiceTemplatePath);
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public File generateGroupFlyer(String groupUid, boolean color, Locale language, String typeOfFile) {
        // load group entity from group repository using uid
        PdfDocument pdfdocument = null;
        PDDocument pd = null;
        File fileToReturn = null;
        Group grpEntity = groupRepository.findOneByUid(groupUid);

        try {

            fileToReturn = File.createTempFile("flyer","pdf");
            fileToReturn.deleteOnExit();

            String flyerToLoad = folderPath + "/" + chooseFlyerToLoad(color, language);

            pdfdocument = new PdfDocument(new PdfReader(flyerToLoad), new PdfWriter(fileToReturn.getAbsolutePath()));

            PdfAcroForm pdfAcroForm = PdfAcroForm.getAcroForm(pdfdocument,true);
            Map<String,PdfFormField> pdfFormFieldMap = pdfAcroForm.getFormFields();

            logger.debug("Fields Map = {}",pdfFormFieldMap);

            logger.info("Form Field = {}",pdfFormFieldMap.get("join_code_header").getValueAsString());

            pdfFormFieldMap.get("group_name").setValue("HOW TO JOIN " + grpEntity.getGroupName().toUpperCase() + " ON GRASSROOT");//**
            pdfFormFieldMap.get("join_code_header").setValue(grpEntity.getGroupTokenCode()).setColor(Color.BLACK);
            pdfFormFieldMap.get("join_code_phone").setValue(grpEntity.getGroupTokenCode()).setColor(Color.BLACK);


            pdfFormFieldMap.get("join_code_header");
            logger.info("Form Field = {}",pdfFormFieldMap.get("join_code_header").getValueAsString());
            logger.info("Form Field = {}",pdfFormFieldMap.get("join_code_phone").getValueAsString());

            pdfAcroForm.flattenFields();

            //pdfOutput.setFullCompression();

            pdfdocument.close();

            pd = PDDocument.load(fileToReturn);
            //generateImage(pd);

            if(typeOfFile.equals("JPEG Image")){
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
        List<Locale> languages = new ArrayList<>();
        File[] filesInFolder = null;
        List<File> tempListOfFiles = new ArrayList<>();

        File folder = new File(folderPath);

        if(!folder.isDirectory()) {
            //Throw exception-------------------------------------???????
            throw new SecurityException("Invalid folder path");
        } else{

            filesInFolder = folder.listFiles();
            logger.info("List of files in folder = {}",filesInFolder.length);

            for(int x = 0;x < filesInFolder.length;x++){
                if(filesInFolder[x].getName().startsWith("group")){
                    tempListOfFiles.add(filesInFolder[x]);
                }
            }

            logger.info("Filtered list of file = {}",tempListOfFiles.size());
            String[] names = new String[tempListOfFiles.size()];

            for(int x = 0;x < tempListOfFiles.size();x++) {
                //names[x] = filesInFolder[x].getName();
                names[x] = tempListOfFiles.get(x).getName();
            }

            for(int x = 0;x < names.length;x++) {
                String[] data = names[x].split(SEPARATOR);
                String name = data[LANGUAGE_POSITION];//Check if is a valid name

                if (Arrays.asList(Locale.getISOLanguages()).contains(name)) {
                    Locale lang = new Locale(name);
                    languages.add(lang);
                }
            }

        }

        logger.info("Languages = {}",languages);

        return languages;
    }

    @Override
    public String chooseFlyerToLoad(boolean color, Locale language) {
        List<Locale> languages = new ArrayList<>();
        File folder = new File(folderPath);
        File[] filesInFolder = null;
        List<File> tempFiles = new ArrayList<>();
        String desiredFlyer = "";
        String flyerToReturn = "";

        if(folder.isDirectory())
        {
            logger.info("Folder Path = {}",folderPath);
            filesInFolder = folder.listFiles();
            for(int x = 0;x < filesInFolder.length;x ++){
                if(filesInFolder[x].getName().startsWith("group")){
                    logger.info("File Name = {}",filesInFolder[x].getName());
                    tempFiles.add(filesInFolder[x]);
                }
            }

            String[] names = new String[tempFiles.size()];

            for(int x = 0;x < tempFiles.size();x++) {
                names[x] = tempFiles.get(x).getName();
                logger.info("The Files = {}",tempFiles.get(x).getName());
            }

            for(int x = 0;x < names.length;x++) {
                String[] data = names[x].split(SEPARATOR);
                String name = data[LANGUAGE_POSITION];//Check if is a valid name

                if (Arrays.asList(Locale.getISOLanguages()).contains(name)) {
                    Locale lang = new Locale(name);
                    languages.add(lang);
                    logger.info("LANGUAGES = {}",languages.get(x));
                }
            }

            String strLang = "";

            for(int x = 0;x < languages.size();x++){
                Locale l = new Locale(languages.get(x).getDisplayName());
                logger.info("Language in my List of Languages = {}",languages.get(x).getDisplayName());
                if(l.getDisplayName().equals(language.getDisplayName())){
                    strLang = languages.get(x).toString();
                    logger.info("-------------Lang = {}",strLang);
                }
            }

            logger.info("ISO3 Language = {}",strLang);
            desiredFlyer = String.format("group_join_code_template_%s_%s.pdf",strLang,color ? "colour" : "grey");
            if(checkFileAvailability(tempFiles,desiredFlyer)){
                flyerToReturn = desiredFlyer;
            } else {
                flyerToReturn = getFallbackFileName(color);
            }

            logger.info("DESIRED FILE = {}" ,desiredFlyer);
        }else{
            throw new SecurityException("Invalid Folder Path");
        }

        return flyerToReturn;
    }

    private String getFallbackFileName(boolean color) {
        return String.format("group_join_code_template_en_%s.pdf", color ? "colour" : "grey");
    }

    public boolean checkFileAvailability(List<File> files, String desiredFlyer) {
        return files.stream().map(File::getName).anyMatch(s -> s.equals(desiredFlyer));
    }
}
