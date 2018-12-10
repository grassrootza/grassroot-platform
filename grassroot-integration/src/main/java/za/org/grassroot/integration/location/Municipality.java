package za.org.grassroot.integration.location;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class Municipality implements Serializable {

    private String name;
    private int id;
    private String type_name;
}
