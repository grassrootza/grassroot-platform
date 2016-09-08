ALTER TABLE notification ADD COLUMN for_android_tl boolean DEFAULT FALSE;
UPDATE notification SET for_android_tl = TRUE WHERE type IN (
  'LOG_BOOK_INFO',
  'LOG_BOOK_REMINDER',
  'EVENT_INFO',
  'EVENT_CHANGED',
  'EVENT_CANCELLED,'
  'EVENT_REMINDER',
  'VOTE_RESULTS',
  'MEETING_RSVP_TOTALS'
);
ALTER TABLE notification ADD COLUMN viewed_android boolean DEFAULT FALSE;
UPDATE notification SET viewed_android = true; -- so that we don't have every Android user seeing X00 unread