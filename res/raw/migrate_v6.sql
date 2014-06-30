DROP INDEX ix_feed_entries;
DROP INDEX ix_recipients_recipient;
DROP INDEX ix_recipients_activity;

CREATE INDEX ix_feed_entries         on feed_entries (account, _ID, activity);
CREATE INDEX ix_recipients_recipient on recipients   (recipient, type, activity);
CREATE INDEX ix_recipients_activity  on recipients   (activity,  type, recipient);