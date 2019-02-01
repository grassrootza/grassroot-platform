package za.org.grassroot.core.domain;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.Set;

public enum StandardRole implements GrantedAuthority {
	ROLE_SYSTEM_ADMIN,
	ROLE_ACCOUNT_ADMIN,
	ROLE_FULL_USER,
	ROLE_LIVEWIRE_USER,
	ROLE_ALPHA_TESTER,
	ROLE_SYSTEM_CALL,
	; // just for inter-service comms


	@Override
	public String getAuthority() {
		return name();
	}
}
