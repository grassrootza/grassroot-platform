package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.webapp.model.rest.NotificationDTO;

import java.util.List;

/**
 * Created by paballo on 2016/04/13.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationWrapper {

    private static final Logger log = LoggerFactory.getLogger(NotificationWrapper.class);

    private List<NotificationDTO> notifications;
    private Integer pageNumber;
    private Integer nextPage;
    private Integer previousPage;
    private  Integer totalPages;

    public NotificationWrapper(List<NotificationDTO> listOfDTOs) {
        this.notifications = listOfDTOs;
        this.totalPages = 1;
        this.pageNumber = 1;
    }

    public NotificationWrapper(Page<Notification> page, List<NotificationDTO> listOfDTOs) {

        this.notifications = listOfDTOs;
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber() + 1;
        if(page.hasNext()){
            nextPage = pageNumber +1;
        }
        if(page.hasPrevious()){
            previousPage = pageNumber - 1;
        }
        log.info("Assembled wrapper, with {} notifications", notifications.size());
    }

    public List<NotificationDTO> getNotifications() {
        return notifications;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getNextPage() {
        return nextPage;
    }

    public Integer getPreviousPage() {
        return previousPage;
    }

    public Integer getTotalPages() {
        return totalPages;
    }
}
