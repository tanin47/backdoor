# --- !Ups

ALTER TABLE "migratedb_test_user" ADD COLUMN "age" INT;


# --- !Downs

ALTER TABLE "migratedb_test_user" DROP COLUMN "age";
