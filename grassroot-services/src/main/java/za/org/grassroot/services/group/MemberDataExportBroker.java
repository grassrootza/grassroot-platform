package za.org.grassroot.services.group;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

public interface MemberDataExportBroker {

    XSSFWorkbook exportGroup(String groupUid, String userUid);

    XSSFWorkbook exportMultipleGroupMembers(List<String> userGroupUids, List<String> groupUidsToExport);

    XSSFWorkbook exportTodoData(String userUid, String todoUid);

    void emailTodoResponses(String userUid, String todoUid, String emailAddress);
}
