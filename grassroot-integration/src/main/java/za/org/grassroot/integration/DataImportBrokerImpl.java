package za.org.grassroot.integration;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.Province;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2016/10/19.
 */
@Service
public class DataImportBrokerImpl implements DataImportBroker {

    private static final Logger logger = LoggerFactory.getLogger(DataImportBrokerImpl.class);

    private static final String EMPTY_PLACEHOLDER = "[blank]";

    private DataFormatter dataFormatter;
    private FormulaEvaluator formulaEvaluator;

    @PostConstruct
    private void init() {
        dataFormatter = new DataFormatter();
    }

    @Override
    public List<String> extractFirstRowOfCells(File file) {
        List<String> firstRow = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Row row = wb.getSheetAt(0).getRow(0);
            int lastColumn = row.getLastCellNum();
            for (int cn = 0; cn < lastColumn; cn++) {
                Cell cell = row.getCell(cn, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                firstRow.add(extractHeader(cell));
            }
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        logger.info("extracted first row of cells, look like: {}", firstRow);
        return firstRow;
    }

    private String extractHeader(Cell cell) {
        final String header = dataFormatter.formatCellValue(cell, formulaEvaluator);
        return StringUtils.isEmpty(header) ? EMPTY_PLACEHOLDER : header;
    }

    @Override
    public List<MembershipInfo> processMembers(File file, boolean headerRow, Integer phoneColumn, Integer nameColumn, Integer roleColumn, Integer emailCol, Integer provinceCol) {
        List<MembershipInfo> importedMembers = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);
            for (int i = headerRow ? 1 : 0; i <  sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                MembershipInfo member = memberFromRow(row, nameColumn, phoneColumn, emailCol, provinceCol, roleColumn);
                importedMembers.add(member);
                logger.debug("Read in member: {}", member);
            }
            logger.info("Read in file!");
        } catch (IOException e) {
            logger.info("Error! File not found: " + e.toString());
        } catch (InvalidFormatException e) {
            logger.info("Error! Could not read file: " + e.toString());
        }
        return importedMembers;
    }

    @Override
    public List<String> extractFirstColumnOfSheet(File file) {
        List<String> firstColumn = new ArrayList<>();
        try {
            Workbook wb = WorkbookFactory.create(file);
            formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                firstColumn.add(dataFormatter.formatCellValue(row.getCell(0), formulaEvaluator));
            };
        } catch (IOException e) {
            logger.info("Error, file input stream corrupted");
        } catch (InvalidFormatException e) {
            logger.info("Error, invalid file format");
        }
        return firstColumn;
    }

    private MembershipInfo memberFromRow(Row row, int nameCol, Integer phoneCol, Integer emailCol, Integer provinceCol,
                                         Integer roleCol) {
        if (phoneCol == null && emailCol == null) {
            throw new IllegalArgumentException("Error! One of email or phone number columns must be present");
        }

        logger.debug("emailCol : {}, provinceCol: {}", emailCol, provinceCol);
        MembershipInfo info = new MembershipInfo();
        info.setDisplayName(dataFormatter.formatCellValue(row.getCell(nameCol), formulaEvaluator).trim());

        if (phoneCol != null) {
            info.setPhoneNumber(dataFormatter.formatCellValue(row.getCell(phoneCol), formulaEvaluator).trim());
        }

        if (emailCol != null) {
            info.setMemberEmail(dataFormatter.formatCellValue(row.getCell(emailCol), formulaEvaluator).trim());
        }

        if (provinceCol != null) {
            info.setProvince(convertProvince(dataFormatter.formatCellValue(row.getCell(provinceCol), formulaEvaluator).trim()));
        }

        if (roleCol != null) {
            info.setRoleName(convertRoleName(row.getCell(roleCol).getStringCellValue()));
        } else {
            info.setRoleName(BaseRoles.ROLE_ORDINARY_MEMBER);
        }

        logger.info("returning member info: {}", info);

        return info;
    }

    private Province convertProvince(final String cellValue) {
        logger.debug("here's the province: {}", cellValue);
        if (Province.EN_PROVINCE_NAMES.containsKey(cellValue.toLowerCase().trim())) {
            logger.debug("okay, province: {}", cellValue);
            return Province.EN_PROVINCE_NAMES.get(cellValue.toLowerCase().trim());
        } else {
            try {
                return Province.valueOf("ZA_" + cellValue.toUpperCase().trim());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String convertRoleName(final String cellValue) {
        if (cellValue.toLowerCase().contains("organizer")) {
            return BaseRoles.ROLE_GROUP_ORGANIZER;
        }
        if (cellValue.toLowerCase().contains("committee")) {
            return BaseRoles.ROLE_COMMITTEE_MEMBER;
        }
        return BaseRoles.ROLE_ORDINARY_MEMBER;
    }

}
