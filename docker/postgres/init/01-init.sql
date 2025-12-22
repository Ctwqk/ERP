-- Create a non-superuser role for the application (optional, for better security)
-- The POSTGRES_USER from docker-compose already creates a superuser role
-- This is an example if you want a separate application role

-- Uncomment below if you want to create a separate app role:
-- CREATE ROLE app_user WITH LOGIN PASSWORD 'app_password';
-- GRANT ALL PRIVILEGES ON DATABASE erp TO app_user;
-- \c erp
-- GRANT ALL ON SCHEMA public TO app_user;

-- Or if you want to use the existing postgres user but grant specific permissions:
-- \c erp
-- GRANT ALL ON SCHEMA public TO postgres;

-- Example: Create extensions if needed
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

