-- Verificacion de la Fase 2. Ejecutar contra MediGestDB:
--   docker exec -it medigest-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U medigest_app -P "$DB_PASSWORD" -d MediGestDB -C -i /tmp/verify-schema.sql

PRINT '--- Migraciones aplicadas por Flyway ---';
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;
GO

PRINT '--- Tablas (esperadas: 17 = 16 del modelo + flyway_schema_history) ---';
SELECT COUNT(*) AS total_tablas FROM sys.tables;
GO

PRINT '--- Filas por tabla ---';
SELECT t.name AS tabla, SUM(p.rows) AS filas
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY t.name
ORDER BY t.name;
GO

PRINT '--- Indices unicos filtrados (esperados: 6) ---';
SELECT name, filter_definition FROM sys.indexes
WHERE has_filter = 1 ORDER BY name;
GO

PRINT '--- Citas por estado (solo con perfil demo) ---';
SELECT estado, COUNT(*) AS total FROM citas GROUP BY estado ORDER BY estado;
GO
