package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.dto.NotificationDTO;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.services.LogBookService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paballo on 2016/04/13.
 */
public class NotificationWrapper {

    private List<NotificationDTO> notifications;
    private Integer pageNumber;
    private Integer nextPage;
    private Integer previousPage;
    private  Integer totalPages;

    @JsonIgnore //this is a temporary measure until such time we can get LogBook entity directly from Notification entity
    private LogBookService logBookService;

    public NotificationWrapper(Page<Notification> page) {

        this.notifications = getNotifications(page.getContent());
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber() + 1;
        if(page.hasNext()){
            nextPage = pageNumber +1;
        }
        if(page.hasPrevious()){
            previousPage = pageNumber - 1;
        }
    }

    public List<NotificationDTO> getNotifications(List<Notification> notifications) {
        List<NotificationDTO> notificationDTOs = new ArrayList<>();
        for (Notification notification : notifications) {
            if(notification.getNotificationType().equals(NotificationType.EVENT)) {
                notificationDTOs.add(new NotificationDTO(notification));
            }
            else if(notification.getNotificationType().equals(NotificationType.LOGBOOK)){
                LogBook logBook = logBookService.load(notification.getLogBookLog().getLogBookId());
                notificationDTOs.add(new NotificationDTO(notification,logBook));
            }
        }
        return notificationDTOs;

    }
}
