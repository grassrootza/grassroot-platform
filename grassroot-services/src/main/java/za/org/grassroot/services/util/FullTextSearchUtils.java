package za.org.grassroot.services.util;

import com.google.common.collect.Sets;

import java.util.Objects;
import java.util.Set;

public class FullTextSearchUtils {
	private static final Set<String> BOOLEAN_OPEARTORS = Sets.newHashSet("|", "&", "!");

	private FullTextSearchUtils() {
		// utilities
	}

	public static final String encodeAsTsQueryText(String text) {
		Objects.requireNonNull(text);
		
		String[] parts = text.split("\\s+");

		StringBuilder sb = new StringBuilder();
		boolean lastPartIsWord = false;
		for (String part : parts) {
			boolean partIsWord = !BOOLEAN_OPEARTORS.contains(part);
			// in case 2 words appeared in succession, we add logical OR between
			if (partIsWord && lastPartIsWord) {
				sb.append("| ");
			}
			sb.append(part).append(" ");
			lastPartIsWord = partIsWord;
		}
		return sb.toString().trim();
	}
}
