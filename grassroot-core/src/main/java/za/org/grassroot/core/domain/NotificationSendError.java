package za.org.grassroot.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.time.LocalDateTime;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NotificationSendError {

    @Column(name = "error_time")
    private LocalDateTime errorTime;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "notification_status_before")
    @Enumerated(EnumType.STRING)
    private NotificationStatus notificationStatusBeforeError;

    @Column(name = "notification_status_after")
    @Enumerated(EnumType.STRING)
    private NotificationStatus notificationStatusAfterError;

}
