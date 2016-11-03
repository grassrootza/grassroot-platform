package za.org.grassroot.integration;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * Created by luke on 2016/11/03.
 */
public interface PdfGeneratingService {

    File generateInvoice(String billingRecordUid);

}
