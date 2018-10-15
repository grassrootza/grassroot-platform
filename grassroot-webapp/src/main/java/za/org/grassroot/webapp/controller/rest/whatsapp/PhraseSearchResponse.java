package za.org.grassroot.webapp.controller.rest.whatsapp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.JpaEntityType;

import java.util.LinkedHashMap;
import java.util.List;

@Getter @Setter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PhraseSearchResponse {

    private boolean entityFound;
    private JpaEntityType entityType;
    private String entityUid;
    private List<String> responseMessages;
    private LinkedHashMap<String, String> responseMenu; // linked hashmap to enforce ordering
    private RequestDataType requestDataType;
    private LinkedHashMap<JpaEntityType, String> possibleEntities; // used in case there are options

    public static PhraseSearchResponse notFoundResponse() {
        return PhraseSearchResponse.builder().entityFound(false).build();
    }

}
