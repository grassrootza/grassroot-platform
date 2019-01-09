package za.org.grassroot.core.domain;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public enum RoleName {

    ROLE_SYSTEM_ADMIN,
    ROLE_ACCOUNT_ADMIN,
    ROLE_FULL_USER,
    ROLE_LIVEWIRE_USER,
    ROLE_ALPHA_TESTER,
    ROLE_GROUP_ORGANIZER,
    ROLE_COMMITTEE_MEMBER,
    ROLE_ORDINARY_MEMBER,
    ROLE_GROUP_OBSERVER,
    ROLE_SYSTEM_CALL; // just for inter-service comms
}
