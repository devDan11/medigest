-- Hibernate 7 mapea un enum cuyos valores son de un solo carácter (M, F, O) a CHAR(1).
-- La columna se declaró VARCHAR(1) en V1; se alinea el tipo para que ddl-auto=validate pase.
ALTER TABLE pacientes DROP CONSTRAINT ck_pacientes_sexo;

ALTER TABLE pacientes ALTER COLUMN sexo CHAR(1) NULL;

ALTER TABLE pacientes ADD CONSTRAINT ck_pacientes_sexo
    CHECK (sexo IS NULL OR sexo IN ('M', 'F', 'O'));
