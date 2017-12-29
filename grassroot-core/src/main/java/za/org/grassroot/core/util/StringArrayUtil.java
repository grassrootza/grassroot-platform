package za.org.grassroot.core.util;

import org.springframework.util.StringUtils;

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
        if (list == null) {
            return new String[0];
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(list);
        return listToArray(new ArrayList<>(set));
    }

    public static List<String> arrayToList(String[] array) {
        return array != null && array.length != 0 ? new ArrayList<>(Arrays.asList(array)) : new ArrayList<>();
    }

    public static String joinStringList(List<String> strings, String joinChar, Integer maxLength) {
        final String str = String.join(joinChar == null ? ", " : joinChar, strings);
        return maxLength == null ? str : str.substring(maxLength);
    }

    public static boolean isAllEmptyOrNull(List<String> strings) {
        return strings == null || strings.isEmpty() || strings.stream().allMatch(StringUtils::isEmpty);
    }
}
