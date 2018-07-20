package za.org.grassroot.services.util;

import com.google.common.collect.Sets;

import java.util.Objects;
import java.util.Set;

public class FullTextSearchUtils {
	private static final Set<String> BOOLEAN_OPEARTORS = Sets.newHashSet("|", "&", "!");

	private FullTextSearchUtils() {
		// utilities
	}

	public static String encodeAsTsQueryText(String text, boolean searchAndOnly, boolean addWildCard) {
		Objects.requireNonNull(text);
		
		String[] parts = text.split("\\s+");

		StringBuilder sb = new StringBuilder();
		boolean lastPartIsWord = false;
		for (String part : parts) {
			boolean partIsWord = !BOOLEAN_OPEARTORS.contains(part);
			// in case 2 words appeared in succession, we add logical AND / OR between them (depending on parameter)
			if (partIsWord && lastPartIsWord) {
				sb.append(searchAndOnly ? "& " : "| ");
			}
			sb.append(part).append(addWildCard && partIsWord ? ":* " : " ");
			lastPartIsWord = partIsWord;
		}
		return sb.toString().trim();
	}
}
