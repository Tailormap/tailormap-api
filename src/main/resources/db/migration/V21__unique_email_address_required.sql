-- This migration adds a unique index to the email column in the users table,
-- ensuring that all email addresses are unique, while allowing multiple NULL values.
CREATE UNIQUE INDEX users_email_unique_idx
    ON users (email) WHERE email IS NOT NULL;