-- User.messagingPreference
alter table user_profile add column message_preference_new varchar(50);
update user_profile set message_preference_new = 'SMS' where message_preference = 0;
update user_profile set message_preference_new = 'ANDROID_APP' where message_preference = 1;
update user_profile set message_preference_new = 'WEB_ONLY' where message_preference = 2;
alter table user_profile drop column message_preference;
alter table user_profile rename column message_preference_new to message_preference;

-- User.alert_preference
alter table user_profile add column alert_preference_new varchar(50);
update user_profile set alert_preference_new = 'NOTIFY_ALL_EVENTS' where alert_preference = 0;
update user_profile set alert_preference_new = 'NOTIFY_NEW_EVENTS' where alert_preference = 1;
update user_profile set alert_preference_new = 'NOTIFY_NEW_EVENTS_AND_GROUP_CHANGES' where alert_preference = 2;
alter table user_profile drop column alert_preference;
alter table user_profile rename column alert_preference_new to alert_preference;

-- user_log.userInterface
alter table user_log add column user_interface_new varchar(50);
update user_log set user_interface_new = 'UNKNOWN' where user_interface = 0;
update user_log set user_interface_new = 'USSD' where user_interface = 1;
update user_log set user_interface_new = 'WEB' where user_interface = 2;
update user_log set user_interface_new = 'ANDROID' where user_interface = 3;
alter table user_log alter column user_interface_new set not null;
alter table user_log drop column user_interface;
alter table user_log rename column user_interface_new to user_interface;

-- user_log.user_log_type
alter table user_log add column user_log_type_new varchar(50);
update user_log set user_log_type_new = 'CREATED_IN_DB' where user_log_type = 0;
update user_log set user_log_type_new = 'INITIATED_USSD' where user_log_type = 1;
update user_log set user_log_type_new = 'CREATED_WEB' where user_log_type = 2;
update user_log set user_log_type_new = 'REGISTERED_ANDROID' where user_log_type = 3;
update user_log set user_log_type_new = 'CHANGED_LANGUAGE' where user_log_type = 4;
update user_log set user_log_type_new = 'USER_SESSION' where user_log_type = 5;
update user_log set user_log_type_new = 'USSD_MENU_ACCESSED' where user_log_type = 6;
update user_log set user_log_type_new = 'USSD_INTERRUPTED' where user_log_type = 7;
update user_log set user_log_type_new = 'USSD_DATE_ENTERED' where user_log_type = 8;
update user_log set user_log_type_new = 'USSD_DATE_WRONG' where user_log_type = 9;
update user_log set user_log_type_new = 'USER_SKIPPED_NAME' where user_log_type = 10;
update user_log set user_log_type_new = 'DEREGISTERED_ANDROID' where user_log_type = 11;
alter table user_log alter column user_log_type_new set not null;
alter table user_log drop column user_log_type;
alter table user_log rename column user_log_type_new to user_log_type;

-- group_log.group_log_type
alter table group_log add column group_log_type_new varchar(50);
update group_log set group_log_type_new = 'GROUP_ADDED' where group_log_type = 0;
update group_log set group_log_type_new = 'GROUP_REMOVED' where group_log_type = 1;
update group_log set group_log_type_new = 'GROUP_UPDATED' where group_log_type = 2;
update group_log set group_log_type_new = 'GROUP_RENAMED' where group_log_type = 3;
update group_log set group_log_type_new = 'GROUP_MEMBER_ADDED' where group_log_type = 4;
update group_log set group_log_type_new = 'GROUP_MEMBER_REMOVED' where group_log_type = 5;
update group_log set group_log_type_new = 'SUBGROUP_ADDED' where group_log_type = 6;
update group_log set group_log_type_new = 'SUBGROUP_REMOVED' where group_log_type = 7;
update group_log set group_log_type_new = 'PERMISSIONS_CHANGED' where group_log_type = 8;
update group_log set group_log_type_new = 'REMINDER_DEFAULT_CHANGED' where group_log_type = 9;
update group_log set group_log_type_new = 'DESCRIPTION_CHANGED' where group_log_type = 10;
update group_log set group_log_type_new = 'TOKEN_CHANGED' where group_log_type = 11;
update group_log set group_log_type_new = 'DISCOVERABLE_CHANGED' where group_log_type = 12;
update group_log set group_log_type_new = 'LANGUAGE_CHANGED' where group_log_type = 13;
update group_log set group_log_type_new = 'PARENT_CHANGED' where group_log_type = 14;
update group_log set group_log_type_new = 'MESSAGE_SENT' where group_log_type = 15;
update group_log set group_log_type_new = 'GROUP_MEMBER_ADDED_VIA_JOIN_CODE' where group_log_type = 16;
update group_log set group_log_type_new = 'GROUP_MEMBER_ADDED_AT_CREATION' where group_log_type = 17;
alter table group_log alter column group_log_type_new set not null;
alter table group_log drop column group_log_type;
alter table group_log rename column group_log_type_new to group_log_type;

-- event_log.event_log_type
alter table event_log add column event_log_type_new varchar(50);
update event_log set event_log_type_new = 'CREATED' where event_log_type = 0;
update event_log set event_log_type_new = 'REMINDER' where event_log_type = 1;
update event_log set event_log_type_new = 'CHANGE' where event_log_type = 2;
update event_log set event_log_type_new = 'CANCELLED' where event_log_type = 3;
update event_log set event_log_type_new = 'MINUTES' where event_log_type = 4;
update event_log set event_log_type_new = 'RSVP' where event_log_type = 5;
update event_log set event_log_type_new = 'TEST' where event_log_type = 6;
update event_log set event_log_type_new = 'RESULT' where event_log_type = 7;
update event_log set event_log_type_new = 'MANUAL_REMINDER' where event_log_type = 8;
update event_log set event_log_type_new = 'FREE_FORM_MESSAGE' where event_log_type = 9;
update event_log set event_log_type_new = 'RSVP_TOTAL_MESSAGE' where event_log_type = 10;
update event_log set event_log_type_new = 'THANK_YOU_MESSAGE' where event_log_type = 11;
alter table event_log alter column event_log_type_new set not null;
alter table event_log drop column event_log_type;
alter table event_log rename column event_log_type_new to event_log_type;

-- account_log.account_log_type
alter table account_log add column account_log_type_new varchar(50);
update account_log set account_log_type_new = 'ACCOUNT_CREATED' where account_log_type = 0;
update account_log set account_log_type_new = 'ADMIN_CHANGED' where account_log_type = 1;
update account_log set account_log_type_new = 'DETAILS_CHANGED' where account_log_type = 2;
update account_log set account_log_type_new = 'GROUP_ADDED' where account_log_type = 3;
update account_log set account_log_type_new = 'GROUP_REMOVED' where account_log_type = 4;
update account_log set account_log_type_new = 'FEATURES_CHANGED' where account_log_type = 5;
update account_log set account_log_type_new = 'MESSAGE_SENT' where account_log_type = 6;
alter table account_log alter column account_log_type_new set not null;
alter table account_log drop column account_log_type;
alter table account_log rename column account_log_type_new to account_log_type;
