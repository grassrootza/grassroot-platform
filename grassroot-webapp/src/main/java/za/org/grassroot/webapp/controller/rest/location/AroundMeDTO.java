package za.org.grassroot.webapp.controller.rest.location;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@ApiModel
@Getter @AllArgsConstructor
public class AroundMeDTO {

    private final String uid;
    private final GeoLocatedEntityType type;

    private final String title;
    private final String description;

    private final String ancestorGroupName;

    private final boolean fetchingUserIsMember;

    private final String contactName;

    private final Long entityDateTimeInMillis;

    private final double latitude;
    private final double longitude;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AroundMeDTO that = (AroundMeDTO) o;

        if (!uid.equals(that.uid)) return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = uid.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
}
