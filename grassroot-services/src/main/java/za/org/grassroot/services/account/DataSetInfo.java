package za.org.grassroot.services.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL) @Getter @Setter @Builder @ToString
public class DataSetInfo {

    private String dataSetLabel;
    private String description;
    private long usersCount;
    private long usersHistoryCount;
    private long userSessionCount;
    private long notificationsCount;
    private Instant start;
    private Instant end;

}
