package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter @Setter @NoArgsConstructor
public class ManagedPagesResponse extends ServiceResponse {

    Map<String, String> managedPages;

    public ManagedPagesResponse(boolean isUserConnectionValid, Map<String, String> managedPages) {
        super(isUserConnectionValid);
        this.managedPages = managedPages;
    }

}
