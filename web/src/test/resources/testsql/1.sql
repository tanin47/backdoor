# --- !Ups

CREATE TABLE "migratedb_test_user"
(
    id TEXT PRIMARY KEY DEFAULT ('user-' || gen_random_uuid()),
    username TEXT NOT NULL,
    hashed_password TEXT NOT NULL,
    password_expired_at TIMESTAMP
);

# --- !Downs

DROP TABLE "migratedb_test_user";
