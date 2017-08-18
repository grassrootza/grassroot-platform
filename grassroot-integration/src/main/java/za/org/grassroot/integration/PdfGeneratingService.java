package za.org.grassroot.integration;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

/**
 * Created by luke on 2016/11/03.
 */
public interface PdfGeneratingService {

    File generateInvoice(List<String> billingRecordUids);

    File generateGroupFlyer(String groupUid, boolean color, Locale language)throws FileNotFoundException;

    List<Locale> availableLanguages();

}
