package za.org.grassroot.core.repository;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;

import java.util.Set;

public interface TodoContainerRepository {
	Set<Todo> findAll(JpaEntityType logBookContainerType, String logBookContainerUid);
}
