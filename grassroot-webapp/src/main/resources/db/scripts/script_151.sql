ALTER TABLE group_user_membership
  ADD COLUMN user_join_method VARCHAR(255) DEFAULT 'ADDED_BY_GROUP_ADMIN';
ALTER TABLE notification
  ADD COLUMN use_only_free_channels BOOLEAN DEFAULT FALSE;