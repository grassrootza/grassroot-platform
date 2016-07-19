CREATE INDEX group_name_fts_idx ON group_profile USING gin(to_tsvector('english', name));
