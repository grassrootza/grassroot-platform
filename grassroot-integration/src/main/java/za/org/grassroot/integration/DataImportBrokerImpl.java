package za.org.grassroot.integration;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.dto.MembershipInfo;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/10/19.
 */
@Service
public class DataImportBrokerImpl implements DataImportBroker {

    private static final Logger logger = LoggerFactory.getLogger(DataImportBrokerImpl.class);

    private DataFormatter dataFormatter;
    private FormulaEvaluator formulaEvaluator;

    @PostConstruct
    private void init() {
        dataFormatter = new DataFormatter();
    }

    @Override
    public List<String> extractFirstRowOfCells(MultipartFile file) {
        List<String> firstRow = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file.getInputStream());
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Row row = wb.getSheetAt(0).getRow(0);
            for (Cell cell : row) {
                firstRow.add(dataFormatter.formatCellValue(cell, formulaEvaluator));
            }
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        return firstRow;
    }

    @Override
    public Set<MembershipInfo> importExcelFile(MultipartFile file, Integer nameColumn, Integer phoneColumn, Integer roleColumn) {
        Set<MembershipInfo> importedMembers = new HashSet<>();
        try {
            Workbook wb = WorkbookFactory.create(file.getInputStream());
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);
            int nameRef = nameColumn == null ? 0 : nameColumn;
            int phoneRef = phoneColumn == null ? 1 : phoneColumn;

            for (Row row : sheet) {
                MembershipInfo member = memberFromRow(row, nameRef, phoneRef, roleColumn);
                importedMembers.add(member);
                logger.info("Read in member: {}", member);
            }
            logger.info("Read in file!");
        } catch (IOException e) {
            logger.info("Error! File not found: " + e.toString());
        } catch (InvalidFormatException e) {
            logger.info("Error! Could not read file: " + e.toString());
        }
        return importedMembers;
    }

    private MembershipInfo memberFromRow(Row row, int nameCol, int phoneCol, Integer roleCol) {
        String roleName = roleCol != null ? convertRoleName(row.getCell(roleCol).getStringCellValue())
                : BaseRoles.ROLE_ORDINARY_MEMBER;

        return new MembershipInfo(dataFormatter.formatCellValue(row.getCell(phoneCol), formulaEvaluator), roleName,
                row.getCell(nameCol).getStringCellValue());
    }

    private String convertRoleName(final String cellValue) {
        // todo : use a more sophisticated form of parsing (bring in Selo?)
        if (cellValue.toLowerCase().contains("organizer")) {
            return BaseRoles.ROLE_GROUP_ORGANIZER;
        }
        if (cellValue.toLowerCase().contains("committee")) {
            return BaseRoles.ROLE_COMMITTEE_MEMBER;
        }
        return BaseRoles.ROLE_ORDINARY_MEMBER;
    }

}
