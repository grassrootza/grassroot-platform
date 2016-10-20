package za.org.grassroot.webapp.model.web;

import java.util.List;

/**
 * Created by luke on 2016/10/20.
 */
public class ExcelSheetAnalysis {

    private List<String> firstRowCells;

    public ExcelSheetAnalysis(List<String> firstRowCells) {
        this.firstRowCells = firstRowCells;
    }

    public List<String> getFirstRowCells() {
        return firstRowCells;
    }

    public void setFirstRowCells(List<String> firstRowCells) {
        this.firstRowCells = firstRowCells;
    }
}
