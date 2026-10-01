-- One-way: owner has added contact; the contact is not asked and need not add the owner back
CREATE TABLE contacts (
    id         UUID PRIMARY KEY,
    owner_id   UUID        NOT NULL REFERENCES users (id),
    contact_id UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (owner_id, contact_id)
);
