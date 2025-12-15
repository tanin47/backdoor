# --- !Ups

CREATE TABLE "backdoor_user"
(
    id TEXT PRIMARY KEY DEFAULT ('user-' || gen_random_uuid()),
    username TEXT NOT NULL UNIQUE,
    hashed_password TEXT NOT NULL,
    password_expired_at TIMESTAMP
);

# --- !Downs

DROP TABLE "backdoor_user";
