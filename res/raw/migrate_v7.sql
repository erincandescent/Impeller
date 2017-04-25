CREATE TABLE outbox (
    _ID         INTEGER PRIMARY KEY,
    account     INTEGER REFERENCES accounts(_ID),
    _json       TEXT,
    status      INTEGER,
    mediaType   TEXT
);