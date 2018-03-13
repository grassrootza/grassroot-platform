package za.org.grassroot.webapp.controller.rest.home;

import lombok.Getter;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.media.MediaFileRecord;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class PublicLiveWireDTO {

    private String headline;
    private long creationTimeMillis;
    private String description;
    private List<String> imageKeys;

    public PublicLiveWireDTO(LiveWireAlert alert) {
        this.headline = alert.getHeadline();
        this.creationTimeMillis = alert.getCreationTime().toEpochMilli();
        this.description = alert.getDescription();
        this.imageKeys = alert.getMediaFiles().stream().map(MediaFileRecord::getKey).collect(Collectors.toList());
    }

}
