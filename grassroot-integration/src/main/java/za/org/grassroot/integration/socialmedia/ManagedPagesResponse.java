package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ManagedPagesResponse extends ServiceResponse {

    List<ManagedPage> managedPages;

    public ManagedPagesResponse(boolean isUserConnectionValid, List<ManagedPage> managedPages) {
        super(isUserConnectionValid);
        this.managedPages = managedPages;
    }

}
