package za.org.grassroot.webapp.model.rest;

import java.util.List;

/**
 * Created by luke on 2016/10/20.
 */
public class ExcelSheetAnalysis {

    private final String tmpFilePath;
    private List<String> firstRowCells;

    public ExcelSheetAnalysis(String tmpFilePath, List<String> firstRowCells) {
        this.tmpFilePath = tmpFilePath;
        this.firstRowCells = firstRowCells;
    }

    public String getTmpFilePath() {
        return tmpFilePath;
    }

    public List<String> getFirstRowCells() {
        return firstRowCells;
    }

    public void setFirstRowCells(List<String> firstRowCells) {
        this.firstRowCells = firstRowCells;
    }
}
