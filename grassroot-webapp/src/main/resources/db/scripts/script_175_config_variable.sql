  create table config_variable (
    id bigserial not null,
    created_date_time timestamp not null,
    key_col text not null,
    update_date_time timestamp,
    value_col text not null,
    description text,
    primary key (id),
    unique (key_col)
  );