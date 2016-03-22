package za.org.grassroot.services;

import za.org.grassroot.core.domain.LogBookRequest;

public interface LogBookRequestBroker {
	LogBookRequest create(String userUid, String groupUid);

	void finish(String logBookUid);
}
