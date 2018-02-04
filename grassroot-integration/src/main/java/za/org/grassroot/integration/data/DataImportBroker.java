package za.org.grassroot.integration.data;

import za.org.grassroot.core.dto.MembershipInfo;

import java.io.File;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
public interface DataImportBroker {

    List<String> extractFirstRowOfCells(File file);

    MemberImportResult processMembers(File file, boolean headerRow, Integer phoneColumn, Integer nameColumn,
                                        Integer roleColumn, Integer emailCol, Integer provinceCol,
                                        Integer firstNameCol, Integer surnameCol, Integer afiilCol);

    List<String> extractFirstColumnOfSheet(File file);

}
