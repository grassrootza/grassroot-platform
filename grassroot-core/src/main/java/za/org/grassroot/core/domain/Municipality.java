package za.org.grassroot.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter @Setter
public class Municipality implements Serializable {
    @JsonProperty("name")
    private String name;
    @JsonProperty("id")
    private int id;
    @JsonProperty("type_name")
    private String type_name;

    public Municipality(){}

    public Municipality(String name,int id,String type_name){
        this.name = name;
        this.id = id;
        this.type_name = type_name;
    }
}
