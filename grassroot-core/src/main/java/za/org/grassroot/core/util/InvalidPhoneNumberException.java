package za.org.grassroot.core.util;

/**
 * Created by luke on 2016/08/06.
 */
public class InvalidPhoneNumberException extends RuntimeException {

	public InvalidPhoneNumberException(String inputNumber) {
		super(inputNumber);
	}

}
