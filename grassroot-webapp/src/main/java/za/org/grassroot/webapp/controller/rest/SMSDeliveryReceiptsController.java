package za.org.grassroot.webapp.controller.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.NotificationStatus;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.webapp.model.AatMsgStatus;
import za.org.grassroot.webapp.model.SMSDeliveryStatus;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;


@Api("/api/inbound/sms/")
@RestController
@RequestMapping("/api/inbound/sms/")
public class SMSDeliveryReceiptsController {

    private static final Logger log = LoggerFactory.getLogger(SMSDeliveryReceiptsController.class);

    private final NotificationService notificationService;

    private static final String FROM_PARAMETER = "fn";
    private static final String TO_PARAMETER = "tn";
    private static final String SUCCESS_PARAMETER = "sc";
    private static final String REF_PARAMETER = "rf";
    private static final String STATUS_PARAMETER = "st";
    private static final String TIME_PARAMETER = "ts";

    private LinkedBlockingQueue<DeliveryReceipt> queue = new LinkedBlockingQueue<>();
    private List<Worker> workers = new ArrayList<>();


    @Autowired
    public SMSDeliveryReceiptsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }


    @RequestMapping(value = "receipt", method = RequestMethod.GET)
    @ApiOperation(value = "Obtain a delivery receipt (or failure)", notes = "Callback for when the gateway notifies us of " +
            "the result of sending an SMS.")
    public void deliveryReceipt(
            @RequestParam(value = FROM_PARAMETER) String fromNumber,
            @RequestParam(value = TO_PARAMETER, required = false) String toNumber,
            @RequestParam(value = SUCCESS_PARAMETER, required = false) String success,
            @RequestParam(value = REF_PARAMETER) String msgKey,
            @RequestParam(value = STATUS_PARAMETER) Integer status,
            @RequestParam(value = TIME_PARAMETER, required = false) String time) {

        log.info("IncomingSMSController -" + " message delivery receipt from number: {}, message key: {}", fromNumber, msgKey);

        queue.add(new DeliveryReceipt(msgKey, status));
    }

    class Worker extends Thread {
        public void run() {

            while (true) {
                try {
                    DeliveryReceipt dlr = queue.take();
                    handleReceipt(dlr.getMessageKey(), dlr.getStatus());
                } catch (InterruptedException e) {
                    // it' ok if we catch that, it will happen on
                }
            }
        }

        private void handleReceipt(@RequestParam(value = REF_PARAMETER) String msgKey, @RequestParam(value = STATUS_PARAMETER) Integer status) {
            Notification notification = notificationService.loadBySeningKey(msgKey);
            if (notification != null) {
                AatMsgStatus aatMsgStatus = AatMsgStatus.fromCode(status);
                SMSDeliveryStatus deliveryStatus = aatMsgStatus.toSMSDeliveryStatus();
                if (deliveryStatus == SMSDeliveryStatus.DELIVERED)
                    notificationService.updateNotificationStatus(notification.getUid(), NotificationStatus.DELIVERED, null, null);
                else if (deliveryStatus == SMSDeliveryStatus.DELIVERY_FAILED)
                    notificationService.updateNotificationStatus(notification.getUid(), NotificationStatus.DELIVERY_FAILED, "Message delivery failed: " + aatMsgStatus.name(), null);
            }
        }

    }


    @Getter
    @AllArgsConstructor
    private class DeliveryReceipt {
        private String messageKey;
        private Integer status;
    }


    @PostConstruct
    public void init() {

        int numberOfWorkers = 1; // increase to get better

        for (int i = 0; i < numberOfWorkers; i++) {
            Worker worker = new Worker();
            worker.start();
            workers.add(worker);
        }
    }

    @PreDestroy
    public void destroy() {
        for (Worker worker : workers) {
            worker.interrupt();
        }
    }


}
