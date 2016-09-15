package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.metamodel.Todo_;

/**
 * Created by luke on 2016/09/15.
 */
public class TodoSpecifications {

    public static Specification<Todo> hasGroupAsParent(final Group group) {
        return (root, query, cb) -> cb.and(cb.equal(root.get(Todo_.parentGroup), group));
    }
}
