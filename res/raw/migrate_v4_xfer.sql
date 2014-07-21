INSERT
    INTO objects (account, id, objectType, published, updated, _json)
    SELECT
            1, id, objectType, published, updated, _json
        FROM objects_old;

CREATE INDEX ix_objects_by_just_id ON objects (id, _ID);
CREATE INDEX ix_objects_old_by_id ON objects_old (id, author, inReplyTo);

UPDATE objects SET
    author    = (
        SELECT _ID FROM objects AS a_n WHERE a_n.id=(
            SELECT author FROM objects_old WHERE objects_old.id=objects.id)),
    inReplyTo = (
        SELECT _ID from objects AS a_n WHERE a_n.id = (
            SELECT inReplyTo from objects_old where objects_old.id=objects.id));

DROP INDEX ix_objects_by_just_id;

INSERT
    INTO activities (_ID, account, id, verb, actor, object, target, published)
    SELECT
            (SELECT _ID FROM objects WHERE id=activities_old.id),       -- ID
            1,                                                          -- account
            id,                                                         -- id
            verb,                                                       -- verb
            (SELECT _ID FROM objects WHERE id=activities_old.actor),    -- actor
            (SELECT _ID FROM objects WHERE id=activities_old.object),   -- object
            (SELECT _ID FROM objects WHERE id=activities_old.target),   -- target
            published                                                   -- published
        FROM activities_old;

INSERT
    INTO feed_entries (account, activity)
    SELECT
            1, -- account
            (SELECT _ID FROM objects WHERE id=feed_entries_old.id)
        FROM feed_entries_old
        WHERE account=(SELECT name FROM accounts WHERE _ID=1);