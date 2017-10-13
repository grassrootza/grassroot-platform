alter table notification drop column sending_status;
alter table notification drop column last_status_change;
alter table notification drop column sending_key:
alter table notification drop column delivery_channel ;
alter table notification drop column send_only_after;


alter table notification add column next_attempt_time timestamp;
alter table notification add column last_attempt_time timestamp;
alter table notification add column read boolean default false;
alter table notification add column delivered boolean default false;
alter table notification add column for_android_tl boolean default false;
alter table notification add column viewed_android boolean default false;




