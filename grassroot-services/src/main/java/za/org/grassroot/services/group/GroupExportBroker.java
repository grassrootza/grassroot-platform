package za.org.grassroot.services.group;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

public interface GroupExportBroker {

    XSSFWorkbook exportGroup(String groupUid);

    XSSFWorkbook exportMultipleGroupMembers(List<String> groupUids);
}
