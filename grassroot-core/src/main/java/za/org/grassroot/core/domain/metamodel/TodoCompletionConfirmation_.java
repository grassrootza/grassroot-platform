package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.TodoCompletionConfirmation;
import za.org.grassroot.core.domain.User;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(TodoCompletionConfirmation.class)
public abstract class TodoCompletionConfirmation_ {

	public static volatile SingularAttribute<TodoCompletionConfirmation, Todo> todo;
	public static volatile SingularAttribute<TodoCompletionConfirmation, Instant> completionTime;
	public static volatile SingularAttribute<TodoCompletionConfirmation, User> member;
	public static volatile SingularAttribute<TodoCompletionConfirmation, Long> id;

}

