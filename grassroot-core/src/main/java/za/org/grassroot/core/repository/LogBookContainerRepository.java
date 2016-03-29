package za.org.grassroot.core.repository;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;

import java.util.Set;

public interface LogBookContainerRepository {
	Set<LogBook> findAll(JpaEntityType logBookContainerType, String logBookContainerUid);
}
