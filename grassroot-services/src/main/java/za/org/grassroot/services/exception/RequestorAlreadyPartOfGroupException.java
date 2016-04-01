package za.org.grassroot.services.exception;

public class RequestorAlreadyPartOfGroupException extends RuntimeException {
	public RequestorAlreadyPartOfGroupException(String message) {
		super(message);
	}
}
