package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.TodoCompletionConfirmation;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import javax.annotation.Generated;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(Todo.class)
public abstract class Todo_ extends AbstractTodoEntity_ {

	public static volatile SingularAttribute<Todo, Double> completionPercentage;
	public static volatile SingularAttribute<Todo, Integer> numberOfRemindersLeftToSend;
	public static volatile SetAttribute<Todo, TodoCompletionConfirmation> completionConfirmations;
	public static volatile SetAttribute<Todo, User> assignedMembers;
	public static volatile SingularAttribute<Todo, Group> replicatedGroup;
	public static volatile SingularAttribute<Todo, Boolean> cancelled;
	public static volatile SingularAttribute<Todo, Group> ancestorGroup;
	public static volatile SingularAttribute<Todo, Instant> completedDate;

}

