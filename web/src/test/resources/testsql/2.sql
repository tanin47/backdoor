# --- !Ups

ALTER TABLE "migratedb_test_user" DROP COLUMN "password_expired_at";


# --- !Downs

ALTER TABLE "migratedb_test_user" ADD COLUMN "password_expired_at" TIMESTAMP;
