package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.media.ImageRecord_;
import za.org.grassroot.core.domain.media.ImageRecord;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/02/23.
 */
public final class ImageRecordSpecifications {

    public static Specification<ImageRecord> actionLogType(ActionLogType actionLogType) {
        return (root, query, cb) -> cb.equal(root.get(ImageRecord_.actionLogType), actionLogType);
    }

    public static Specification<ImageRecord> actionLogUid(String actionLogUid) {
        return (root, query, cb) -> cb.equal(root.get(ImageRecord_.actionLogUid), actionLogUid);
    }

}
