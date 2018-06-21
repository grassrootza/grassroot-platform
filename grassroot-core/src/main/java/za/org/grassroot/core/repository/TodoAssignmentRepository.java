package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;

import java.util.List;

public interface TodoAssignmentRepository extends JpaRepository<TodoAssignment, Long>,
        JpaSpecificationExecutor<TodoAssignment> {

    TodoAssignment findByTodoAndUser(Todo todo, User user);

    List<TodoAssignment> findByTodo(Todo todo);
}
