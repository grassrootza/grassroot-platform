package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.Todo_;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.domain.task.TodoLog_;
import za.org.grassroot.core.enums.TodoLogType;

import javax.persistence.criteria.Join;

/**
 * Created by luke on 2017/02/25.
 */
public final class TodoLogSpecifications {

    public static Specification<TodoLog> forTodo(Todo t) {
        return (root, query, cb) -> cb.equal(root.get(TodoLog_.todo), t);
    }

    public static Specification<TodoLog> forUser(User u) {
        return (root, query, cb) -> cb.equal(root.get(TodoLog_.user), u);
    }

    public static Specification<TodoLog> forGroup(Group g) {
        return (root, query, cb) -> {
          Join<TodoLog, Todo> join = root.join(TodoLog_.todo);
          return cb.equal(join.get(Todo_.ancestorGroup), g);
        };
    }

    public static Specification<TodoLog> ofType(TodoLogType type) {
        return (root, query, cb) -> cb.equal(root.get(TodoLog_.type), type);
    }

}
