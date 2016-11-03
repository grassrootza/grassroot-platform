package za.org.grassroot.integration;

import java.io.File;

/**
 * Created by luke on 2016/11/03.
 */
public interface PdfGeneratingService {

    File generateInvoice(String billingRecordUid);

}
