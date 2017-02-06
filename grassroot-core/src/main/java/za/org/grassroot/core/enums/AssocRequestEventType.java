package za.org.grassroot.core.enums;

public enum AssocRequestEventType {
    OPENED,
    APPROVED,
    DECLINED,
    CANCELLED,
    REMINDED,
    ABORTED // for when it is approved, but then there is a failure on follow through (e.g., on account sponsorship & payment fails)
}
