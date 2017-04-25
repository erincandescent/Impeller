DROP INDEX IF EXISTS ix_activities;
DROP INDEX IF EXISTS ix_activities_related;
DROP INDEX IF EXISTS ix_objects;
DROP INDEX IF EXISTS ix_objects_inReplyTo;
DROP INDEX IF EXISTS ix_replies;
DROP INDEX IF EXISTS ix_feed_entries;

CREATE TABLE accounts (
    _ID         INTEGER PRIMARY KEY,
    name        TEXT UNIQUE
);
CREATE INDEX ix_accounts
    ON accounts
        (name);

CREATE TABLE objects (
    _ID         INTEGER PRIMARY KEY,
    account     INTEGER REFERENCES accounts(_ID),

    id          TEXT NOT NULL,                          -- Object ID
    objectType  TEXT,                                   -- ActivityStreams object type
    author      INTEGER REFERENCES objects(_ID),        -- Object ID
    published   INTEGER,                                -- Date.getTime()
    updated     INTEGER,                                -- Date.getTime()
    inReplyTo   INTEGER REFERENCES objects(_ID),        -- Object ID
    _json       TEXT                                    -- JSON copy
);
CREATE INDEX ix_objects_ids
    ON objects
        (account, id);
CREATE INDEX ix_objects_inReplyTo
    ON objects
        (account, inReplyTo, published);

CREATE TABLE activities (
    -- INT because we DON'T want the standard rowid behaviour inferred.
    _ID         INT     PRIMARY KEY REFERENCES objects(_ID),
    account     INTEGER REFERENCES accounts(_ID),

    id          TEXT NOT NULL,                          -- Activity ID
    verb        TEXT NOT NULL,                          -- verb
    actor       INTEGER REFERENCES objects(_ID),        -- Object ID
    object      INTEGER REFERENCES objects(_ID),        -- Object ID
    target      INTEGER REFERENCES objects(_ID),        -- Object ID
    published   INTEGER                                 -- Date.getTime(), i.e. millis since Unix epoch
);
CREATE INDEX ix_activity_ids
    ON activities
        (account, id);

CREATE INDEX ix_activities_related
    ON activities
        (account, object, verb);

CREATE TABLE recipients (
    _ID         INTEGER PRIMARY KEY,
    recipient   INTEGER REFERENCES objects(_ID),
    activity    INTEGER REFERENCES activities(_ID) REFERENCES objects(_ID)
    -- type     SHORT INT (v5)
);

CREATE INDEX ix_recipients_activity
    ON recipients
        (activity);
CREATE INDEX ix_recipients_recipient
    ON recipients
        (recipient);

CREATE TABLE feed_entries (
    _ID         INTEGER PRIMARY KEY,
    account     INTEGER REFERENCES accounts(_ID),
    activity    INTEGER REFERENCES activities(_ID)
);

CREATE INDEX ix_feed_entries
    ON feed_entries
        (account, _ID DESC);