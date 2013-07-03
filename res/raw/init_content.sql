-- Activities
CREATE TABLE activities (
    _ID         INTEGER PRIMARY KEY,
    id          TEXT NOT NULL UNIQUE,   -- Activity ID
    verb        TEXT NOT NULL,          -- Object ID
    actor       TEXT NOT NULL,          -- Object ID
    object      TEXT,                   -- Object ID
    target      TEXT,                   -- Object ID
    published   INTEGER                 -- Date.getTime(), i.e. millis since Unix epoch
);

CREATE INDEX ix_activities ON activities (
    id,
    actor,
    object,
    published
);
-- To get the JSON version of an activity, *join* on the Object with the same ID

-- Objects
CREATE TABLE objects (
    _ID         INTEGER PRIMARY KEY,
    id          TEXT NOT NULL UNIQUE,   -- Object ID
    objectType  TEXT,                   -- ActivityStreams object type
    author      TEXT,                   -- Object ID
    published   INTEGER,                -- Date.getTime()
    updated     INTEGER,                -- Date.getTime()
    inReplyTo   TEXT,                   -- Object ID
    _json       TEXT                    -- JSON copy
);
CREATE INDEX ix_objects ON objects (
    id,
    objectType,
    author,
    published,
    updated
);

CREATE INDEX ix_replies ON objects (
    inReplyTo,
    published
);

-- Feeds
CREATE TABLE feed_entries (
    _ID         INTEGER PRIMARY KEY,
    account     TEXT,       -- Account name
    id          TEXT,       -- Activity ID
    published   INTEGER     -- Publication time
);

CREATE INDEX ix_feed_entries ON feed_entries (
    account, published
);