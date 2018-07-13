package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.GroupDefaultImage;

import java.util.Optional;

/**
 * Created by luke on 2016/09/26.
 */
public interface GroupImageBroker {

    void setGroupImageToDefault(String userUid, String groupUid, GroupDefaultImage defaultImage, boolean removeCustomImage);

    void saveGroupImage(String userUid, String groupUid, String imageUrl, byte[] image);

    Optional<Group> getGroupByUidOrImageUrl(String uidOrImageUrl);

}
