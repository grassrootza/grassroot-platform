package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event<VoteContainer> {

	private static final String RANDOMIZE_TAG = "RANDOMIZE";

	private static final String OPTION_PREFIX = "OPTION::";
	private static final String LANGUAGE_PREFIX = "LANGUAGE::";

	@ManyToOne
	@JoinColumn(name = "parent_meeting_id")
	protected Meeting parentMeeting;

	private Vote() {
		// for JPA
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent) {
		this(name, startDateTime, user, parent, false);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups) {
		this(name, startDateTime, user, parent, includeSubGroups, null);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups, String description) {
		super(startDateTime, user, parent, name, includeSubGroups, EventReminderType.DISABLED, 0, description, true, false);
		setParent(parent);
	}

	public boolean hasOption(String option) {
		if (getTags() == null)
			return false;

		return getVoteOptions().stream().anyMatch(tag -> tag.trim().equalsIgnoreCase(option.trim()));
	}

	public List<String> getVoteOptions() {
		List<String> options = getPrefixFilteredList(OPTION_PREFIX).map(s -> s.substring(OPTION_PREFIX.length())).collect(Collectors.toList());

		if (shouldRandomize()) {
			Collections.shuffle(options);
		}

		return options;
	}

	public void setRandomize(boolean randomize) {
		if (randomize) {
			this.addTag(RANDOMIZE_TAG);
		} else {
			this.removeTag(RANDOMIZE_TAG);
		}
	}

	private boolean shouldRandomize() {
		return this.getTagList().contains(RANDOMIZE_TAG);
	}

	public void addLanguagePrompt(Locale language, String prompt) {
		this.getLanguagePrompt(language).ifPresent(existing -> this.removeTag(LANGUAGE_PREFIX + "::" + language.toString() + "::" + existing));
		this.addTag(LANGUAGE_PREFIX + "::" + language.toString() + "::" + prompt);
	}

	public Optional<String> getLanguagePrompt(Locale language) {
		return this.getPrefixFilteredList(LANGUAGE_PREFIX)
				.filter(s -> s.startsWith(language.toString()))
				.findFirst().map(s -> s.substring(language.toString().length()));
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

    @Override
    public TaskType getTaskType() {
        return TaskType.VOTE;
    }

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.VOTE;
	}

	public VoteContainer getParent() {
		if (parentGroup != null) {
			return parentGroup;
		} else if (parentTodo != null) {
			return parentTodo;
		} else if (parentMeeting != null) {
			return parentMeeting;
		} else {
			throw new IllegalStateException("There is no " + VoteContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

    public void setParent(VoteContainer parent) {
		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
		} else if (parent instanceof Todo) {
			this.parentTodo = (Todo) parent;
		} else if (parent instanceof Meeting) {
			this.parentMeeting = (Meeting) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
