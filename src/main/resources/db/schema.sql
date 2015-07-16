CREATE TABLE "user"
(
  id                SERIAL PRIMARY KEY      NOT NULL,
  phone_number      VARCHAR(20)             NOT NULL,
  created_date_time TIMESTAMP DEFAULT now() NOT NULL
);
CREATE TABLE "group"
(
  id                SERIAL PRIMARY KEY      NOT NULL,
  name              VARCHAR                 NOT NULL,
  created_by_user   INT                     NULL,
  created_date_time TIMESTAMP DEFAULT now() NOT NULL
);
CREATE TABLE event
(
  id                SERIAL PRIMARY KEY      NOT NULL,
  location          VARCHAR                 NOT NULL,
  start_date_time   TIMESTAMP               NOT NULL,
  created_by_user   INT                     NOT NULL,
  applies_to_group  INT                     NOT NULL,
  created_date_time TIMESTAMP DEFAULT now() NOT NULL
);
CREATE TABLE group_user_membership
(
  user_id           INT                     NOT NULL,
  group_id          INT                     NOT NULL,
  created_date_time TIMESTAMP DEFAULT now() NOT NULL
);
CREATE TABLE event_user_invitation
(
  user_id           INT                     NOT NULL,
  event_id          INT                     NOT NULL,
  created_by_user   INT                     NOT NULL,
  created_date_time TIMESTAMP DEFAULT now() NOT NULL
);
ALTER TABLE event ADD FOREIGN KEY (created_by_user) REFERENCES "user" (id);
ALTER TABLE event_user_invitation ADD FOREIGN KEY (event_id) REFERENCES event (id);
ALTER TABLE event_user_invitation ADD FOREIGN KEY (created_by_user) REFERENCES "user" (id);
ALTER TABLE event_user_invitation ADD FOREIGN KEY (user_id) REFERENCES "user" (id);
ALTER TABLE "group" ADD FOREIGN KEY (created_by_user) REFERENCES "user" (id);
CREATE UNIQUE INDEX unique_id ON "group" (id);
ALTER TABLE group_user_membership ADD FOREIGN KEY (group_id) REFERENCES "group" (id);
ALTER TABLE group_user_membership ADD FOREIGN KEY (user_id) REFERENCES "user" (id);
CREATE UNIQUE INDEX "unique_id" ON "user" (id);
CREATE UNIQUE INDEX unique_phone_number ON "user" (phone_number);
