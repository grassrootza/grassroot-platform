package za.org.grassroot.integration;

import za.org.grassroot.core.dto.MembershipInfo;

import java.io.File;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
public interface DataImportBroker {

    List<String> extractFirstRowOfCells(File file);

    List<MembershipInfo> processMembers(File file, boolean headerRow, Integer phoneColumn, Integer nameColumn,
                                        Integer roleColumn, Integer emailCol, Integer provinceCol);

    List<String> extractFirstColumnOfSheet(File file);

}
