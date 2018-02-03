package za.org.grassroot.integration.data;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.dto.MembershipInfo;

import java.util.List;

@Getter @Setter
public class MemberImportResult {

    List<MembershipInfo> processedMembers;
    List<String[]> errorRows;
    String errorFilePath;

}
