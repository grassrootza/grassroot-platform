create table campaign (
  id bigserial not null,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  start_date_time timestamp not null,
  end_date_time timestamp not null,
  name varchar(255),
  code varchar(3),
  created_by_user bigint,
  version integer,
  tags text[] default '{}',
  primary key (id));

  create table campaign_message (
  id bigserial not null,
  uid varchar(50) not null,
  created_date_time timestamp not null,
  message varchar(255),
  created_by_user bigint,
  version integer,
  primary key (id));


  CREATE SEQUENCE campaign_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

  CREATE SEQUENCE campaign_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

  ALTER SEQUENCE campaign_id_seq OWNED BY campaign.id;

  ALTER SEQUENCE campaign_message_id_seq OWNED BY campaign_message.id;