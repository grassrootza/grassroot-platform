package za.org.grassroot.webapp.controller.rest.file;

import lombok.*;
import za.org.grassroot.core.domain.media.MediaFunction;

@NoArgsConstructor @Builder @Getter @Setter @ToString
public class MediaUploadResult {

    private String mediaFileKey;
    private String mediaFileBucket;

    private String imageUrl;

    private String mediaRecordUid;
    private MediaFunction mediaFunction;

}
