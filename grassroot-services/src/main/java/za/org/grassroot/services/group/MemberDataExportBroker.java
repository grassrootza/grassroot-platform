package za.org.grassroot.services.group;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.dto.group.GroupLogDTO;

import java.time.Instant;
import java.util.List;

public interface MemberDataExportBroker {

    XSSFWorkbook exportGroup(String groupUid, String userUid);

    XSSFWorkbook exportGroupErrorReport(String groupUid, String userUid);

    XSSFWorkbook exportGroupMembersFiltered(String groupUid, String userUid, List<String> memberUids);

    XSSFWorkbook exportMultipleGroupMembers(List<String> userGroupUids, List<String> groupUidsToExport);

    XSSFWorkbook exportCampaignJoinedData(String campaignUid, String userUid);

    XSSFWorkbook exportTodoData(String userUid, String todoUid);

    void emailTodoResponses(String userUid, String todoUid, String emailAddress);

    XSSFWorkbook exportInboundMessages(List<GroupLogDTO> inboundMessages);

    XSSFWorkbook exportNotificationErrorReport(List<? extends Notification> notifications);

    XSSFWorkbook exportNotificationStdReport(List<? extends Notification> notifications);

    XSSFWorkbook exportAccountActivityReport(String accountUid, Instant start, Instant end);

    XSSFWorkbook exportWhatsappOptedInUsers();

}
