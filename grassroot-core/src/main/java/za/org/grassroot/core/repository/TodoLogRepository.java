package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.TodoLogType;

import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface TodoLogRepository extends JpaRepository<TodoLog, Long>, JpaSpecificationExecutor<TodoLog> {
	TodoLog findOneByUid(String uid);
	TodoLog findFirstByTodoAndTypeOrderByCreationTimeDesc(Todo todo, TodoLogType type);
	List<TodoLog> findByType(TodoLogType type, Pageable pageable);

}
