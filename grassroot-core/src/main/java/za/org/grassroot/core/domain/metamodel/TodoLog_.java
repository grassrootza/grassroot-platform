package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TodoLogType;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(TodoLog.class)
public abstract class TodoLog_ {

	public static volatile SingularAttribute<TodoLog, Todo> todo;
	public static volatile SingularAttribute<TodoLog, Instant> createdDateTime;
	public static volatile SingularAttribute<TodoLog, Long> id;
	public static volatile SingularAttribute<TodoLog, String> message;
	public static volatile SingularAttribute<TodoLog, TodoLogType> type;
	public static volatile SingularAttribute<TodoLog, User> user;

}

