CREATE TABLE notification_error (
  notification_id            BIGINT NOT NULL,
  error_time                 TIMESTAMP,
  error_message              CHARACTER VARYING(2048),
  notification_status_before CHARACTER VARYING(255),
  notification_status_after  CHARACTER VARYING(255)
);

ALTER TABLE notification_error
  ADD CONSTRAINT notification_error_notification_id_fk
FOREIGN KEY (notification_id) REFERENCES notification (id);

CREATE INDEX notification_error_notification_id_index
  ON notification_error (notification_id);