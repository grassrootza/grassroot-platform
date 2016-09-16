package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.TodoCompletionConfirmType;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

/**
 * Created by luke on 2016/09/16.
 */
@StaticMetamodel(TodoCompletionConfirmation.class)
public abstract class TodoCompletionConfirmation_ {

    public static volatile SingularAttribute<TodoCompletionConfirmation, Long> id;
    public static volatile SingularAttribute<TodoCompletionConfirmation, User> member;
    public static volatile SingularAttribute<TodoCompletionConfirmation, TodoCompletionConfirmType> confirmType;

}
