package za.org.grassroot.core.domain.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Entity @Slf4j
@DiscriminatorValue("VOTE")
public class Vote extends Event<VoteContainer> {

	// Could also add as columns, but these are very seldom used, the other event (meeting) doesn't need them, and they've changed often
	private static final String RANDOMIZE_TAG = "RANDOMIZE";
	private static final String EXCLUDE_ABSTAIN_TAG = "EXCLUDE_ABSTENTION_OPTION";
	private static final String NO_NOTIFICATIONS_TAG = "SEND_NO_NOTIFICATIONS";

	private static final String OPTION_PREFIX = "OPTION::";
	private static final String LANGUAGE_PREFIX = "LANGUAGE::";
	private static final String POST_MSG_PREFIX = "POSTVOTE::";
	private static final String LANG_OPTION_PREFIX = "LANG_OPTION::";

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

	public void setVoteOptions(List<String> options) {
		Objects.requireNonNull(options);
		// first, remove all of the existing ones
		this.getPrefixFilteredList(OPTION_PREFIX).forEach(oldOption -> this.removeTag(OPTION_PREFIX + oldOption));
		this.addTags(options.stream().map(newOption -> OPTION_PREFIX + newOption).collect(Collectors.toList()));
	}

	public boolean hasOption(String option) {
		if (getTags() == null)
			return false;

		return getVoteOptions().stream().anyMatch(tag -> tag.trim().equalsIgnoreCase(option.trim()));
	}

	public List<String> getVoteOptions() {
		List<String> options = getPrefixFilteredList(OPTION_PREFIX).collect(Collectors.toList());

		if (shouldRandomize()) {
			Collections.shuffle(options);
		}

		return options;
	}

	public void setRandomize(boolean randomize) {
		this.toggleTagBasedFlag(randomize, RANDOMIZE_TAG);
	}

	private boolean shouldRandomize() {
		return this.getTagList().contains(RANDOMIZE_TAG);
	}

	public void setExcludeAbstention(boolean excludeAbstention) {
		toggleTagBasedFlag(excludeAbstention, EXCLUDE_ABSTAIN_TAG);
	}

	public boolean shouldExcludeAbstention() {
		return this.getTagList().contains(EXCLUDE_ABSTAIN_TAG);
	}

	public void setStopNotifications(boolean stopNotifications) {
		this.toggleTagBasedFlag(stopNotifications, NO_NOTIFICATIONS_TAG);
	}

	public boolean shouldStopNotifications() {
		return this.getTagList().contains(NO_NOTIFICATIONS_TAG);
	}

	private void toggleTagBasedFlag(boolean turnOn, String tag) {
		if (turnOn)
			this.addTag(tag);
		else
			this.removeTag(tag);
	}

	// all of the following are for mass votes only, and allow for language handling etc
	public boolean hasAdditionalLanguagePrompts() {
		return this.getPrefixFilteredList(LANGUAGE_PREFIX).findFirst().isPresent();
	}

	public List<Locale> getPromptLanguages() {
		return this.getPrefixFilteredList(LANGUAGE_PREFIX)
				.map(s -> s.substring(0, s.indexOf("::")))
				.map(Locale::new).collect(Collectors.toList());
	}

	public Optional<String> getLanguagePrompt(Locale language) {
		if (language == null)
			return Optional.of(this.getName());
		else
			return this.getPrefixFilteredList(LANGUAGE_PREFIX)
				.filter(s -> s.startsWith(language.toString()))
				.findFirst().map(s -> s.substring((language.toString() + "::").length()));
	}

	public void setLangaugePrompts(Map<Locale, String> languagePrompts) {
		languagePrompts.forEach((locale, s) -> addLanguagePrompt(locale, s.trim()));
	}

	private void addLanguagePrompt(Locale language, String prompt) {
		this.getLanguagePrompt(language).ifPresent(existing -> this.removeTag(LANGUAGE_PREFIX + language.toString() + "::" + existing));
		this.addTag(LANGUAGE_PREFIX + language.toString() + "::" + prompt);
	}

	public boolean hasPostVotePrompt() {
		return this.getPrefixFilteredList(POST_MSG_PREFIX).findFirst().isPresent();
	}

	public Optional<String> getPostVotePrompt(Locale language) {
		return this.getPrefixFilteredList(POST_MSG_PREFIX)
				.filter(s -> s.startsWith(language.toString()))
				.findFirst().map(s -> s.substring((language.toString() + "::").length()));
	}

	public void setPostVotePrompts(Map<Locale, String> postVotePrompts) {
		postVotePrompts.forEach(((locale, s) -> addPostVotePrompt(locale, s.trim())));
	}

	private void addPostVotePrompt(Locale language, String postVoteMsg) {
		final Locale lang = language == null ? Locale.ENGLISH : language;
		this.getPostVotePrompt(lang).ifPresent(existing -> this.removeTag(POST_MSG_PREFIX + lang.toString() + "::" + existing));
		this.addTag(POST_MSG_PREFIX + lang.toString() + "::" + postVoteMsg);
	}

	public void removePostVotePrompts() {
	    this.getPrefixFilteredList(POST_MSG_PREFIX).forEach(prompt -> this.removeTag(POST_MSG_PREFIX + prompt));
    }

	public void addMultiLangOptions(Locale language, Map<String, String> translatedOptions) {
		// this is going to get messy so store order and reference option
		final String template = "${prefix}${language}::${refIndex}::${option}";
		translatedOptions.forEach((orig, trans) -> {
			int refIndex = getUnshuffledOptions().indexOf(orig);
			Map<String, String> substitutes = new HashMap<>();
			substitutes.put("prefix", LANG_OPTION_PREFIX);
			substitutes.put("language", language.toString());
			substitutes.put("refIndex", "" + refIndex);
			substitutes.put("option", trans);
			final StringSubstitutor strSub = new StringSubstitutor(substitutes);
			final String tagToAdd = strSub.replace(template);
			log.debug("Assembled language vote option: {}", tagToAdd);
			this.addTag(tagToAdd);
		});
	}

	public boolean hasMultiLangOptions() {
		return this.getPrefixFilteredList(LANG_OPTION_PREFIX).findFirst().isPresent();
	}

	public List<String> getOptionsForLang(Locale language, List<String> optionsInDesiredOrder) {
		List<String> optionsWithIndex = this.getPrefixFilteredList(LANG_OPTION_PREFIX + language.toString() + "::")
				.collect(Collectors.toList());

		if (optionsWithIndex.isEmpty())
			return getVoteOptions();

		// in case it has been shuffled, we need to get a map of the shuffle
        final Map<Integer, Integer> shuffleMap = getOptionsReshuffleMap(optionsInDesiredOrder);
        log.info("Resulting map: {}", shuffleMap);

		SortedMap<Integer, String> sortedMap = optionsWithIndex.stream().collect(Collectors.toMap(
				optionWithIndex -> {
				    final int shuffledInt = shuffleMap.get(Integer.parseInt(optionWithIndex.substring(0, 1)));
				    log.info("For option {}, returning key : {}", optionWithIndex, shuffledInt);
				    return shuffledInt;
                },
                optionWithIndex -> optionWithIndex.substring("0::".length()),
				(v1,v2) -> { throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));}, TreeMap::new));

		return new ArrayList<>(sortedMap.values());
	}

	private Map<Integer, Integer> getOptionsReshuffleMap(List<String> possiblyShuffledOptions) {
	    Map<Integer, Integer> returnMap = new HashMap<>();
	    final List<String> unshuffledOptions = getUnshuffledOptions();
	    log.info("Processing options, shuffled: {}, unshuffled: {}", possiblyShuffledOptions, unshuffledOptions);
	    for (int i = 0; i < possiblyShuffledOptions.size(); i++) {
	        final String thisOption = possiblyShuffledOptions.get(i);
            returnMap.put(unshuffledOptions.indexOf(thisOption), i);
        }
	    return returnMap;
    }

    public List<String> getUnshuffledOptions() {
        return this.getPrefixFilteredList(OPTION_PREFIX).collect(Collectors.toList());
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

	@Override
	public String toString() {
		return "Vote{" +
				"id=" + id +
				", name='" + name + '\'' +
				", tags=" + Arrays.toString(tags) +
				", createdDateTime=" + createdDateTime +
				", eventStartDateTime=" + eventStartDateTime +
				", createdByUser=" + createdByUser.getName() +
				", parentGroup=" + parentGroup.getName() +
				'}';
	}
}
