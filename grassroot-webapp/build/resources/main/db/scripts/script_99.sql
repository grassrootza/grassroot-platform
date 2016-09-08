CREATE INDEX event_name_fts_idx ON event USING gin(to_tsvector('english', name));
CREATE INDEX log_book_message_fts_idx ON log_book USING gin(to_tsvector('english', message));
