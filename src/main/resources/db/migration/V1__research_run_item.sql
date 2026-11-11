-- Research module (ADR-0004 item 9): the inbox queue and retry list. Owned by dev.solomaster.research.
-- One row per intake item; the vault keeps the briefs themselves, so this table holds state only.
CREATE TABLE run_item (
    id          INTEGER PRIMARY KEY,
    channel     TEXT    NOT NULL,                -- where the item came from, e.g. 'playlist'
    item_key    TEXT    NOT NULL,                -- the item's identity in that channel, e.g. a video ID
    title       TEXT    NOT NULL,
    status      TEXT    NOT NULL CHECK (status IN ('queued', 'done', 'failed')),
    attempts    INTEGER NOT NULL DEFAULT 0,
    last_error  TEXT,                            -- plain reason shown on the page; never keys or provider text
    brief_name  TEXT,                            -- note name under SoloMaster/Research/Briefs/ once done
    created_at  TEXT    NOT NULL,                -- ISO-8601 instants
    updated_at  TEXT    NOT NULL,
    UNIQUE (channel, item_key)
);
