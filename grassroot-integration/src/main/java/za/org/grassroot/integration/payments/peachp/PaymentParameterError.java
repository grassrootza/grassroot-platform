package za.org.grassroot.integration.payments.peachp;

/**
 * Created by luke on 2016/11/01.
 */
public class PaymentParameterError {

    private String name;
    private String value;
    private String message;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ParamError{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
