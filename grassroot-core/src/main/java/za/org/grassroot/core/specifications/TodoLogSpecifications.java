package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.domain.task.TodoLog_;
import za.org.grassroot.core.enums.TodoLogType;

/**
 * Created by luke on 2017/02/25.
 */
public final class TodoLogSpecifications {

    public static Specification<TodoLog> forTodo(Todo t) {
        return (root, query, cb) -> cb.equal(root.get(TodoLog_.todo), t);
    }

    public static Specification<TodoLog> ofType(TodoLogType type) {
        return (root, query, cb) -> cb.equal(root.get(TodoLog_.type), type);
    }

}
