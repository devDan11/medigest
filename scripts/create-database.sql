IF DB_ID(N'MediGestDB') IS NULL
BEGIN
    CREATE DATABASE MediGestDB COLLATE Latin1_General_100_CI_AI;
END
GO

USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'medigest_app')
BEGIN
    EXEC(N'CREATE LOGIN medigest_app WITH PASSWORD = N''$(APP_PASSWORD)'', CHECK_POLICY = ON');
END
GO

USE MediGestDB;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'medigest_app')
BEGIN
    CREATE USER medigest_app FOR LOGIN medigest_app;
END
GO
ALTER ROLE db_owner ADD MEMBER medigest_app;
GO
