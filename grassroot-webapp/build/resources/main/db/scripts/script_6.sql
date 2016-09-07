CREATE INDEX idx_event_start_date_time
  ON event
  USING btree
  (start_date_time);
analyze event;