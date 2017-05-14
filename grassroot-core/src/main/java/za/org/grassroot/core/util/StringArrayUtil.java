package za.org.grassroot.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by luke on 2017/05/13.
 */
public class StringArrayUtil {

    public static String[] listToArray(List<String> list) {
        String[] array = new String[list.size()];
        return list.toArray(array);
    }

    public static String[] listToArrayRemoveDuplicates(List<String> list) {
        LinkedHashSet<String> set = new LinkedHashSet<>(list);
        return listToArray(new ArrayList<>(set));
    }

    public static List<String> arrayToList(String[] array) {
        return array != null ? Arrays.asList(array) : new ArrayList<>();
    }
}
