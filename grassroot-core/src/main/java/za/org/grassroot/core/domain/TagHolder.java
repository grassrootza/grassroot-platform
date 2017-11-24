package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.StringArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface TagHolder {

    String[] getTags();
    void setTags(String[] tags);

    default void addTag(String tag) {
        Objects.requireNonNull(tag);
        // next line is messy but necessary for default method else abstract method throws null error
        List<String> tags = new ArrayList<>(StringArrayUtil.arrayToList(getTags()));
        tags.add(tag);
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

    default void setTags(List<String> tags) {
        setTags(StringArrayUtil.listToArrayRemoveDuplicates(tags));
    }
}
