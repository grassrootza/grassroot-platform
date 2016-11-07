ALTER TABLE group_message_stats ADD COLUMN sender bigint,
ADD CONSTRAINT fk_sender_id FOREIGN KEY(sender) REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION