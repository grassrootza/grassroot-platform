package za.org.grassroot.integration;

import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.dto.MembershipInfo;

import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/10/19.
 */
public interface DataImportBroker {

    // todo: error throwing etc
    List<String> extractFirstRowOfCells(MultipartFile file);

    Set<MembershipInfo> importExcelFile(MultipartFile file, Integer nameColumn, Integer phoneColumn, Integer roleColumn);

}
