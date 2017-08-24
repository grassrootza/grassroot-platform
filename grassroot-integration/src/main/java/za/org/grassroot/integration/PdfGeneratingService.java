package za.org.grassroot.integration;


import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

/**
 * Created by luke on 2016/11/03.
 */
public interface PdfGeneratingService {

    File generateInvoice(List<String> billingRecordUids);

    File generateGroupFlyer(String groupUid, boolean color, Locale language, String typeOfFile)throws FileNotFoundException;

    File generateImage(PDDocument pdDocument)throws FileNotFoundException;

    List<Locale> availableLanguages();

    String chooseFlyerToLoad(boolean color, Locale language);

}
