CREATE USER backdoor_test_user WITH PASSWORD 'test';
CREATE DATABASE backdoor_test;
GRANT ALL PRIVILEGES ON DATABASE backdoor_test to backdoor_test_user;
ALTER ROLE backdoor_test_user superuser;
