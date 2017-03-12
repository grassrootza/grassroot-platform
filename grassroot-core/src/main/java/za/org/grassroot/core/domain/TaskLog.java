package za.org.grassroot.core.domain;

import za.org.grassroot.core.domain.geo.GeoLocation;

/**
 * Created by luke on 2017/03/10.
 * todo : check if we can use this to clean up ImageRecord
 */
public interface TaskLog<T extends Task> extends ActionLog {

    User getUser();

    T getTask();

    GeoLocation getLocation();

}
