package za.org.grassroot.services;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ChangedSinceWrapper<T> {
	private final List<T> addedAndUpdated;
	private final Set<String> removedUids;

	public ChangedSinceWrapper(List<T> addedAndUpdated, Set<String> removedUids) {
		this.addedAndUpdated = addedAndUpdated;
		this.removedUids = removedUids;
	}

	public List<T> getAddedAndUpdated() {
		return addedAndUpdated;
	}

	public Set<String> getRemovedUids() {
		return removedUids;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("ChangedSinceWrapper{");
		sb.append("addedAndUpdated=").append(addedAndUpdated);
		sb.append(", removedUids=").append(removedUids);
		sb.append('}');
		return sb.toString();
	}
}
