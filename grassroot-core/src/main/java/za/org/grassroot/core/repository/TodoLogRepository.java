package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.enums.TodoLogType;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface TodoLogRepository extends JpaRepository<TodoLog, Long> {
	TodoLog findFirstByTodoAndTypeOrderByCreatedDateTimeDesc(Todo todo, TodoLogType type);

}
