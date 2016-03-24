package za.org.grassroot.core.domain;

public interface UidIdentifiableEntity {
	JpaEntityType getJpaEntityType();
	String getUid();
}
