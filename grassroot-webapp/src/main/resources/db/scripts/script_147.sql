alter table notification add column sending_status varchar(255) default 'READ';
alter table notification add column last_status_change timestamp;
alter table notification add column sending_key varchar(255);
alter table notification add column delivery_channel varchar(255) default 'SMS';
alter table notification add column send_only_after timestamp;

--todo(beegor) carefully check what updates needs to be done for migration of existing data
update notification set delivery_channel = (CASE when for_android_tl = TRUE THEN 'ANDROID_APP' else 'SMS' END);
update notification set last_status_change = last_attempt_time;

update notification set sending_status =
(CASE when for_android_tl = false THEN 'READ' else (case when READ = true then 'READ' when delivered = true then 'DELIVERED' ELSE 'SENT' END ) END);


alter table notification drop column next_attempt_time;
alter table notification drop column last_attempt_time;
alter table notification drop column read;
alter table notification drop column delivered;
alter table notification drop column for_android_tl;
alter table notification drop column viewed_android;