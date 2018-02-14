package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.StringArrayUtil;

import java.util.*;
import java.util.stream.Collectors;

public interface TagHolder {

    String TOPIC_PREFIX = "TOPIC:"; // for tags, to distinguish topics and other things

    String[] getTags();
    void setTags(String[] tags);

    default void addTag(String tag) {
        Objects.requireNonNull(tag);
        // next line is messy but necessary for default method else abstract method throws null error
        List<String> tags = new ArrayList<>(StringArrayUtil.arrayToList(getTags()));
        tags.add(tag);
        setTags(StringArrayUtil.listToArrayRemoveDuplicates(tags));
    }

    default void addTags(List<String> newTags) {
        Objects.requireNonNull(newTags);
        List<String> tagList = getTagList();
        tagList.addAll(newTags);
        setTags(tagList);
    }

    default void setTags(List<String> tags) {
        setTags(StringArrayUtil.listToArrayRemoveDuplicates(tags));
    }

    default void removeTag(String tag) {
        Objects.requireNonNull(tag);
        // as prior, next line is messy but seems necessary
        List<String> tags = StringArrayUtil.arrayToList(getTags());
        tags.remove(tag);
        setTags(StringArrayUtil.listToArrayRemoveDuplicates(tags));
    }

    default List<String> getTagList() {
        return StringArrayUtil.arrayToList(getTags());
    }

    default List<String> getTopics() {
        return getTagList().stream()
                .filter(s -> s.startsWith(TOPIC_PREFIX))
                .map(s -> s.substring(TOPIC_PREFIX.length()))
                .collect(Collectors.toList());
    }

    default void setTopics(Set<String> topics) {
        // first get all the non-topic tags
        List<String> tags = getTagList().stream()
                .filter(s -> !s.startsWith(TOPIC_PREFIX)).collect(Collectors.toList());
        // then add the topics
        tags.addAll(topics.stream().map(s -> TOPIC_PREFIX + s).collect(Collectors.toSet()));
        setTags(tags);
    }

    default void addTopics(Set<String> topics) {
        List<String> newTopics = topics.stream().map(topic -> TOPIC_PREFIX + topic).collect(Collectors.toList());
        addTags(newTopics);
    }

    default void removeTopics(Collection<String> topics) {
        topics.forEach(topic -> removeTag(TOPIC_PREFIX + topic));
    }

    static String[] convertTopicsToTags(List<String> topics) {
        return StringArrayUtil.listToArrayRemoveDuplicates(
                topics.stream().map(s -> TOPIC_PREFIX + s).collect(Collectors.toList()));
    }
}
