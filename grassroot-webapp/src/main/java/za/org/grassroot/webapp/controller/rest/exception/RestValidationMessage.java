package za.org.grassroot.webapp.controller.rest.exception;

import java.io.Serializable;


public class RestValidationMessage  implements Serializable{

    private static final long serialVersionUID = 2747120380547591755L;
    private final String fieldName;
    private final String message;

    public RestValidationMessage(String fieldName, String message){
        this.fieldName = fieldName;
        this.message = message;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getMessage() {
        return message;
    }
}
