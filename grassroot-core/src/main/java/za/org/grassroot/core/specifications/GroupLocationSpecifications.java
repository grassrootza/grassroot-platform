package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.GroupLocation_;

import java.util.Collection;

/**
 * Created by luke on 2017/04/14.
 */
public final class GroupLocationSpecifications {

    public static Specification<GroupLocation> groupIn(Collection<Group> groups) {
        return (root, query, cb) -> root.get(GroupLocation_.group).in(groups);
    }

}
