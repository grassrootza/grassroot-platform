package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class GenericPostResponse extends ServiceResponse {

    private boolean isPostSuccessful;

    public GenericPostResponse(boolean isUserConnectionValid, boolean isPostSuccessful) {
        super(isUserConnectionValid);
        this.isPostSuccessful = isPostSuccessful;
    }

}
