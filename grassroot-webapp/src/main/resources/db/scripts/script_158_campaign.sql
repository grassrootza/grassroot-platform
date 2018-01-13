create table campaign (
  id bigserial not null,
  version integer,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  start_date_time timestamp not null,
  end_date_time timestamp not null,
  name varchar(50),
  description varchar(512),
  code varchar(10),
  created_by_user bigint,
  tags text[] default '{}',
  ancestor_group_id bigint,
  type varchar(50),
  url varchar(60),
  primary key (id));

create table campaign_message (
  id bigserial not null,
  version integer,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  message varchar(255),
  created_by_user bigint,
  tags text[] default '{}',
  variation varchar(35),
  locale varchar(35),
  channel varchar(35),
  campaign_id bigserial,
  primary key (id));

create table campaign_message_action (
  id bigserial not null,
  version integer,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  parent_message_id bigserial,
  created_by_user bigint,
  action_message_id bigserial,
  action varchar(35),
  primary key (id));

CREATE TABLE campaign_log(
  id bigserial NOT NULL,
  uid varchar(50) NOT NULL,
  creation_time timestamp without time zone not null,
  user_id bigserial,
  campaign_log_type varchar(50) NOT NULL,
  description character varying(255),
  campaign_id bigserial,
  CONSTRAINT uk_campaign_log_uid unique (uid),
  PRIMARY KEY (id));

alter table campaign_message ADD CONSTRAINT fk_campaign_message_campaign_id FOREIGN KEY (campaign_id) REFERENCES campaign(id);
alter table campaign_message_action ADD CONSTRAINT fk_action_message_id FOREIGN KEY (action_message_id) REFERENCES campaign_message(id);
alter table campaign_message_action ADD CONSTRAINT fk_parent_message_id FOREIGN KEY (parent_message_id) REFERENCES campaign_message(id);
alter table campaign_log ADD CONSTRAINT fk_campaign_id FOREIGN KEY (campaign_id) REFERENCES campaign(id);