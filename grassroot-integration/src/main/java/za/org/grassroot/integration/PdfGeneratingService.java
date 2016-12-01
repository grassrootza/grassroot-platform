package za.org.grassroot.integration;

import java.io.File;
import java.util.List;

/**
 * Created by luke on 2016/11/03.
 */
public interface PdfGeneratingService {

    File generateInvoice(List<String> billingRecordUids);

}
