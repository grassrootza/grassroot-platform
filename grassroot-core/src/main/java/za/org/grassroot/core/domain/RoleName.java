package za.org.grassroot.core.domain;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public enum RoleName {
	// maintain the ordering of values since Comparable is taking this into account

	ROLE_GROUP_ORGANIZER,
	ROLE_COMMITTEE_MEMBER,
	ROLE_ORDINARY_MEMBER;
}
