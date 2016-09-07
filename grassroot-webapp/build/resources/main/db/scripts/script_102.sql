ALTER TABLE user_profile ADD COLUMN safety_group_id bigint;
ALTER TABLE user_profile ADD CONSTRAINT fk_user_safety_group FOREIGN KEY(safety_group_id) REFERENCES group_profile(id) MATCH SIMPLE;

CREATE TABLE address
(
  id bigserial NOT NULL,
  created_date_time timestamp without time zone,
  uid character varying(50) NOT NULL,
  resident_user_id bigint NOT NULL,
  house_number character varying(50),
  street_name character varying(50),
  area character varying(50),
  CONSTRAINT address_pkey PRIMARY KEY (id),
  CONSTRAINT fk_user_id FOREIGN KEY (resident_user_id)
      REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uk_address_uid UNIQUE (uid));

  CREATE INDEX idx_address_belongs_to_user
  ON address
  USING btree
  (resident_user_id);


CREATE TABLE safety_event
(
  id bigserial NOT NULL,
  created_date_time timestamp without time zone NOT NULL,
  group_id bigint,
  activated_by_user bigint NOT NULL,
  uid character varying(50) NOT NULL,
  scheduled_reminder_time timestamp without time zone,
  active boolean DEFAULT true,
  false_alarm boolean DEFAULT false,
  responded_to boolean DEFAULT false,
  CONSTRAINT safety_event_pkey PRIMARY KEY (id),
  CONSTRAINT fk_activated_user_profile FOREIGN KEY (activated_by_user)
      REFERENCES user_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT fk_group_safety FOREIGN KEY (group_id)
      REFERENCES group_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uk_safety_event_uid UNIQUE (uid)
);

CREATE INDEX idx_safety_event_applies_to_group
  ON safety_event
  USING btree
  (group_id);

CREATE INDEX idx_safety_event_activated_by_user
  ON safety_event
  USING btree
  (activated_by_user);


