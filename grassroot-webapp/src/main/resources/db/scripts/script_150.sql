-- indexes for columns used in where condition in messaging app queries
CREATE INDEX notification_sending_status_index
  ON notification (sending_status);
CREATE INDEX notification_send_only_after_index
  ON notification (send_only_after);
CREATE INDEX notification_delivery_channel_index
  ON notification (delivery_channel);
CREATE INDEX notification_last_status_change_index
  ON notification (last_status_change);
CREATE INDEX notification_sent_via_provider_index
  ON public.notification (sent_via_provider);