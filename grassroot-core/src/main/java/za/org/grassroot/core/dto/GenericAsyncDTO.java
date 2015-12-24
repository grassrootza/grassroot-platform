package za.org.grassroot.core.dto;

import java.io.Serializable;

/**
 * Created by aakilomar on 12/20/15.
 */
public class GenericAsyncDTO implements Serializable {

    private String identifier;
    private Object object;

    public GenericAsyncDTO(String identifier, Object object) {
        this.identifier = identifier;
        this.object = object;
    }

    public GenericAsyncDTO() {
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    @Override
    public String toString() {
        return "GenericAsyncDTO{" +
                "identifier='" + identifier + '\'' +
                ", object=" + object.toString() +
                '}';
    }
}
