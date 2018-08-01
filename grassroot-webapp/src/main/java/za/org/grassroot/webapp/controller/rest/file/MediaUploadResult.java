package za.org.grassroot.webapp.controller.rest.file;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.core.domain.media.MediaFunction;

@Builder @Getter @Setter @ToString
public class MediaUploadResult {

    private String mediaFileKey;
    private String mediaFileBucket;

    private String imageUrl;

    private String mediaRecordUid;
    private MediaFunction mediaFunction;

}
