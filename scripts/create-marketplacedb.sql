-- Local / RDS: create marketplace-integration DB (separate from shop/stock/order).
-- Run as postgres superuser:
--   psql -U postgres -h localhost -f create-marketplacedb.sql

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'marketplacedb') THEN
    CREATE ROLE marketplacedb LOGIN PASSWORD 'marketplacedb';
  END IF;
END
$$;

SELECT 'CREATE DATABASE marketplacedb OWNER marketplacedb'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'marketplacedb')\gexec

GRANT ALL PRIVILEGES ON DATABASE marketplacedb TO marketplacedb;

\c marketplacedb
GRANT USAGE, CREATE ON SCHEMA public TO marketplacedb;
ALTER SCHEMA public OWNER TO marketplacedb;
