-- =====================================================================
-- MediGest - Esquema base (SQL Server)
--
-- Convenciones:
--   * Los instantes se guardan en UTC como DATETIMEOFFSET(6).
--   * Los horarios semanales (horarios_medicos) son hora local de la clinica
--     (America/Lima) y se convierten a UTC en la capa de servicio.
--   * Nada se elimina fisicamente: se usan banderas "activo" y estados.
--   * Texto en VARCHAR (collation de la base: Latin1_General_100_CI_AI).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Seguridad: roles, usuarios, usuario_roles, tokens_recuperacion
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id      INT IDENTITY(1,1) NOT NULL,
    nombre  VARCHAR(30)       NOT NULL,
    CONSTRAINT pk_roles        PRIMARY KEY (id),
    CONSTRAINT uq_roles_nombre UNIQUE (nombre),
    CONSTRAINT ck_roles_nombre CHECK (nombre IN ('ADMINISTRADOR', 'MEDICO', 'RECEPCIONISTA', 'PACIENTE'))
);

CREATE TABLE usuarios (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    email              VARCHAR(150)         NOT NULL,
    password_hash      VARCHAR(100)         NOT NULL,
    activo             BIT                  NOT NULL CONSTRAINT df_usuarios_activo DEFAULT 1,
    intentos_fallidos  INT                  NOT NULL CONSTRAINT df_usuarios_intentos DEFAULT 0,
    bloqueado_hasta    DATETIMEOFFSET(6)    NULL,
    ultimo_acceso      DATETIMEOFFSET(6)    NULL,
    creado_en          DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_usuarios_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    actualizado_en     DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_usuarios_actualizado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_usuarios        PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_email  UNIQUE (email),
    CONSTRAINT ck_usuarios_intentos CHECK (intentos_fallidos >= 0)
);

CREATE TABLE usuario_roles (
    usuario_id  BIGINT NOT NULL,
    rol_id      INT    NOT NULL,
    CONSTRAINT pk_usuario_roles         PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuario_roles_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_usuario_roles_rol     FOREIGN KEY (rol_id)     REFERENCES roles (id)
);
CREATE INDEX ix_usuario_roles_rol ON usuario_roles (rol_id);

CREATE TABLE tokens_recuperacion (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id  BIGINT               NOT NULL,
    token_hash  VARCHAR(64)          NOT NULL,   -- solo se guarda el hash SHA-256 del token
    expira_en   DATETIMEOFFSET(6)    NOT NULL,
    usado_en    DATETIMEOFFSET(6)    NULL,
    creado_en   DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_tokens_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_tokens_recuperacion PRIMARY KEY (id),
    CONSTRAINT uq_tokens_hash         UNIQUE (token_hash),
    CONSTRAINT fk_tokens_usuario      FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);
CREATE INDEX ix_tokens_usuario ON tokens_recuperacion (usuario_id, expira_en);

-- ---------------------------------------------------------------------
-- Catalogos: especialidades, consultorios
-- ---------------------------------------------------------------------
CREATE TABLE especialidades (
    id                     INT IDENTITY(1,1) NOT NULL,
    nombre                 VARCHAR(80)       NOT NULL,
    descripcion            VARCHAR(300)      NULL,
    duracion_sugerida_min  INT               NOT NULL CONSTRAINT df_especialidades_duracion DEFAULT 30,
    activa                 BIT               NOT NULL CONSTRAINT df_especialidades_activa DEFAULT 1,
    CONSTRAINT pk_especialidades        PRIMARY KEY (id),
    CONSTRAINT uq_especialidades_nombre UNIQUE (nombre),
    CONSTRAINT ck_especialidades_duracion CHECK (duracion_sugerida_min BETWEEN 5 AND 240)
);

CREATE TABLE consultorios (
    id         INT IDENTITY(1,1) NOT NULL,
    codigo     VARCHAR(20)       NOT NULL,
    ubicacion  VARCHAR(100)      NULL,
    activo     BIT               NOT NULL CONSTRAINT df_consultorios_activo DEFAULT 1,
    CONSTRAINT pk_consultorios        PRIMARY KEY (id),
    CONSTRAINT uq_consultorios_codigo UNIQUE (codigo)
);

-- ---------------------------------------------------------------------
-- Personas: pacientes, medicos, medico_especialidades
-- ---------------------------------------------------------------------
CREATE TABLE pacientes (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id                  BIGINT               NULL,
    nombres                     VARCHAR(100)         NOT NULL,
    apellidos                   VARCHAR(100)         NOT NULL,
    tipo_documento              VARCHAR(10)          NOT NULL,
    numero_documento            VARCHAR(20)          NOT NULL,
    fecha_nacimiento            DATE                 NOT NULL,
    sexo                        VARCHAR(1)           NULL,
    email                       VARCHAR(150)         NULL,
    telefono                    VARCHAR(20)          NULL,
    direccion                   VARCHAR(200)         NULL,
    contacto_emergencia_nombre  VARCHAR(100)         NULL,
    contacto_emergencia_telefono VARCHAR(20)         NULL,
    activo                      BIT                  NOT NULL CONSTRAINT df_pacientes_activo DEFAULT 1,
    creado_en                   DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_pacientes_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    actualizado_en              DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_pacientes_actualizado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_pacientes           PRIMARY KEY (id),
    CONSTRAINT uq_pacientes_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT fk_pacientes_usuario   FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_pacientes_tipo_doc  CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE', 'OTRO')),
    CONSTRAINT ck_pacientes_sexo      CHECK (sexo IS NULL OR sexo IN ('M', 'F', 'O'))
);
CREATE UNIQUE INDEX ux_pacientes_usuario ON pacientes (usuario_id) WHERE usuario_id IS NOT NULL;
CREATE INDEX ix_pacientes_apellidos ON pacientes (apellidos, nombres);
CREATE INDEX ix_pacientes_activo    ON pacientes (activo);

CREATE TABLE medicos (
    id                     BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id             BIGINT               NULL,
    nombres                VARCHAR(100)         NOT NULL,
    apellidos              VARCHAR(100)         NOT NULL,
    numero_colegiatura     VARCHAR(20)          NOT NULL,
    email                  VARCHAR(150)         NULL,
    telefono               VARCHAR(20)          NULL,
    consultorio_id         INT                  NULL,
    duracion_consulta_min  INT                  NOT NULL CONSTRAINT df_medicos_duracion DEFAULT 30,
    activo                 BIT                  NOT NULL CONSTRAINT df_medicos_activo DEFAULT 1,
    creado_en              DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_medicos_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    actualizado_en         DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_medicos_actualizado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_medicos               PRIMARY KEY (id),
    CONSTRAINT uq_medicos_colegiatura   UNIQUE (numero_colegiatura),
    CONSTRAINT fk_medicos_usuario       FOREIGN KEY (usuario_id)     REFERENCES usuarios (id),
    CONSTRAINT fk_medicos_consultorio   FOREIGN KEY (consultorio_id) REFERENCES consultorios (id),
    CONSTRAINT ck_medicos_duracion      CHECK (duracion_consulta_min BETWEEN 5 AND 240)
);
CREATE UNIQUE INDEX ux_medicos_usuario ON medicos (usuario_id) WHERE usuario_id IS NOT NULL;
CREATE INDEX ix_medicos_apellidos   ON medicos (apellidos, nombres);
CREATE INDEX ix_medicos_consultorio ON medicos (consultorio_id);
CREATE INDEX ix_medicos_activo      ON medicos (activo);

CREATE TABLE medico_especialidades (
    medico_id        BIGINT NOT NULL,
    especialidad_id  INT    NOT NULL,
    CONSTRAINT pk_medico_especialidades     PRIMARY KEY (medico_id, especialidad_id),
    CONSTRAINT fk_medico_esp_medico         FOREIGN KEY (medico_id)       REFERENCES medicos (id),
    CONSTRAINT fk_medico_esp_especialidad   FOREIGN KEY (especialidad_id) REFERENCES especialidades (id)
);
CREATE INDEX ix_medico_especialidades_esp ON medico_especialidades (especialidad_id);

-- ---------------------------------------------------------------------
-- Disponibilidad: horarios_medicos, bloqueos_horarios
-- ---------------------------------------------------------------------
CREATE TABLE horarios_medicos (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    medico_id        BIGINT               NOT NULL,
    dia_semana       INT                  NOT NULL,   -- ISO: 1 = lunes ... 7 = domingo
    hora_inicio      TIME(0)              NOT NULL,   -- hora local de la clinica
    hora_fin         TIME(0)              NOT NULL,
    descanso_inicio  TIME(0)              NULL,
    descanso_fin     TIME(0)              NULL,
    activo           BIT                  NOT NULL CONSTRAINT df_horarios_activo DEFAULT 1,
    CONSTRAINT pk_horarios_medicos  PRIMARY KEY (id),
    CONSTRAINT uq_horarios_medicos  UNIQUE (medico_id, dia_semana, hora_inicio),
    CONSTRAINT fk_horarios_medico   FOREIGN KEY (medico_id) REFERENCES medicos (id),
    CONSTRAINT ck_horarios_dia      CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horarios_rango    CHECK (hora_fin > hora_inicio),
    CONSTRAINT ck_horarios_descanso CHECK (
        (descanso_inicio IS NULL AND descanso_fin IS NULL)
        OR (descanso_inicio IS NOT NULL AND descanso_fin IS NOT NULL
            AND descanso_fin > descanso_inicio
            AND descanso_inicio >= hora_inicio
            AND descanso_fin <= hora_fin))
);
CREATE INDEX ix_horarios_medico_dia ON horarios_medicos (medico_id, dia_semana);

CREATE TABLE bloqueos_horarios (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    medico_id       BIGINT               NULL,
    consultorio_id  INT                  NULL,
    inicio_utc      DATETIMEOFFSET(6)    NOT NULL,
    fin_utc         DATETIMEOFFSET(6)    NOT NULL,
    tipo            VARCHAR(15)          NOT NULL,
    motivo          VARCHAR(200)         NULL,
    estado          VARCHAR(12)          NOT NULL CONSTRAINT df_bloqueos_estado DEFAULT 'APROBADO',
    creado_por      BIGINT               NULL,
    creado_en       DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_bloqueos_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_bloqueos_horarios    PRIMARY KEY (id),
    CONSTRAINT fk_bloqueos_medico      FOREIGN KEY (medico_id)      REFERENCES medicos (id),
    CONSTRAINT fk_bloqueos_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorios (id),
    CONSTRAINT fk_bloqueos_creado_por  FOREIGN KEY (creado_por)     REFERENCES usuarios (id),
    CONSTRAINT ck_bloqueos_objetivo    CHECK (medico_id IS NOT NULL OR consultorio_id IS NOT NULL),
    CONSTRAINT ck_bloqueos_rango       CHECK (fin_utc > inicio_utc),
    CONSTRAINT ck_bloqueos_tipo        CHECK (tipo IN ('VACACIONES', 'AUSENCIA', 'MANTENIMIENTO', 'OTRO')),
    CONSTRAINT ck_bloqueos_estado      CHECK (estado IN ('SOLICITADO', 'APROBADO', 'RECHAZADO'))
);
CREATE INDEX ix_bloqueos_medico      ON bloqueos_horarios (medico_id, inicio_utc, fin_utc);
CREATE INDEX ix_bloqueos_consultorio ON bloqueos_horarios (consultorio_id, inicio_utc, fin_utc);

-- ---------------------------------------------------------------------
-- Citas e historial
-- ---------------------------------------------------------------------
CREATE TABLE citas (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    codigo_reserva       VARCHAR(20)          NOT NULL,
    paciente_id          BIGINT               NOT NULL,
    medico_id            BIGINT               NOT NULL,
    especialidad_id      INT                  NOT NULL,
    consultorio_id       INT                  NULL,
    inicio_utc           DATETIMEOFFSET(6)    NOT NULL,
    fin_utc              DATETIMEOFFSET(6)    NOT NULL,
    estado               VARCHAR(15)          NOT NULL CONSTRAINT df_citas_estado DEFAULT 'PENDIENTE',
    motivo               VARCHAR(300)         NULL,
    motivo_cancelacion   VARCHAR(300)         NULL,
    cancelada_en         DATETIMEOFFSET(6)    NULL,
    creado_por           BIGINT               NULL,
    modificado_por       BIGINT               NULL,
    creado_en            DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_citas_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    actualizado_en       DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_citas_actualizado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    version              BIGINT               NOT NULL CONSTRAINT df_citas_version DEFAULT 0,
    CONSTRAINT pk_citas                    PRIMARY KEY (id),
    CONSTRAINT uq_citas_codigo             UNIQUE (codigo_reserva),
    CONSTRAINT fk_citas_paciente           FOREIGN KEY (paciente_id)    REFERENCES pacientes (id),
    -- La clave compuesta garantiza que el medico atiende realmente esa especialidad.
    CONSTRAINT fk_citas_medico_especialidad FOREIGN KEY (medico_id, especialidad_id)
        REFERENCES medico_especialidades (medico_id, especialidad_id),
    CONSTRAINT fk_citas_consultorio        FOREIGN KEY (consultorio_id) REFERENCES consultorios (id),
    CONSTRAINT fk_citas_creado_por         FOREIGN KEY (creado_por)     REFERENCES usuarios (id),
    CONSTRAINT fk_citas_modificado_por     FOREIGN KEY (modificado_por) REFERENCES usuarios (id),
    CONSTRAINT ck_citas_rango              CHECK (fin_utc > inicio_utc),
    CONSTRAINT ck_citas_estado             CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'))
);
CREATE INDEX ix_citas_medico_inicio      ON citas (medico_id, inicio_utc);
CREATE INDEX ix_citas_paciente_inicio    ON citas (paciente_id, inicio_utc);
CREATE INDEX ix_citas_consultorio_inicio ON citas (consultorio_id, inicio_utc);
CREATE INDEX ix_citas_estado_inicio      ON citas (estado, inicio_utc);
CREATE INDEX ix_citas_especialidad       ON citas (especialidad_id, inicio_utc);

-- Ultima barrera contra reservas duplicadas (el solapamiento parcial lo valida la capa de
-- servicio bajo bloqueo; SQL Server no tiene restricciones de exclusion).
-- Una cita "ocupa" el horario mientras no este CANCELADA ni NO_ASISTIO.
CREATE UNIQUE INDEX ux_citas_medico_inicio_activa ON citas (medico_id, inicio_utc)
    WHERE estado <> 'CANCELADA' AND estado <> 'NO_ASISTIO';
CREATE UNIQUE INDEX ux_citas_paciente_inicio_activa ON citas (paciente_id, inicio_utc)
    WHERE estado <> 'CANCELADA' AND estado <> 'NO_ASISTIO';
CREATE UNIQUE INDEX ux_citas_consultorio_inicio_activa ON citas (consultorio_id, inicio_utc)
    WHERE consultorio_id IS NOT NULL AND estado <> 'CANCELADA' AND estado <> 'NO_ASISTIO';

CREATE TABLE historial_citas (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    cita_id              BIGINT               NOT NULL,
    tipo_evento          VARCHAR(20)          NOT NULL,
    estado_anterior      VARCHAR(15)          NULL,
    estado_nuevo         VARCHAR(15)          NULL,
    inicio_anterior_utc  DATETIMEOFFSET(6)    NULL,
    inicio_nuevo_utc     DATETIMEOFFSET(6)    NULL,
    motivo               VARCHAR(300)         NULL,
    usuario_id           BIGINT               NULL,
    creado_en            DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_historial_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_historial_citas     PRIMARY KEY (id),
    CONSTRAINT fk_historial_cita      FOREIGN KEY (cita_id)    REFERENCES citas (id),
    CONSTRAINT fk_historial_usuario   FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_historial_tipo      CHECK (tipo_evento IN ('CREADA', 'CONFIRMADA', 'REPROGRAMADA', 'CANCELADA', 'ASISTENCIA', 'CAMBIO_ESTADO')),
    CONSTRAINT ck_historial_est_ant   CHECK (estado_anterior IS NULL OR estado_anterior IN ('PENDIENTE', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO')),
    CONSTRAINT ck_historial_est_nuevo CHECK (estado_nuevo IS NULL OR estado_nuevo IN ('PENDIENTE', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'))
);
CREATE INDEX ix_historial_cita ON historial_citas (cita_id, creado_en);

-- ---------------------------------------------------------------------
-- Lista de espera
-- ---------------------------------------------------------------------
CREATE TABLE lista_espera (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    paciente_id        BIGINT               NOT NULL,
    medico_id          BIGINT               NULL,
    especialidad_id    INT                  NOT NULL,
    fecha_desde        DATE                 NOT NULL,
    fecha_hasta        DATE                 NOT NULL,
    franja             VARCHAR(12)          NOT NULL CONSTRAINT df_espera_franja DEFAULT 'CUALQUIERA',
    estado             VARCHAR(12)          NOT NULL CONSTRAINT df_espera_estado DEFAULT 'ACTIVA',
    oferta_medico_id   BIGINT               NULL,
    oferta_inicio_utc  DATETIMEOFFSET(6)    NULL,
    oferta_fin_utc     DATETIMEOFFSET(6)    NULL,
    oferta_expira_en   DATETIMEOFFSET(6)    NULL,
    cita_id            BIGINT               NULL,
    creado_en          DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_espera_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    actualizado_en     DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_espera_actualizado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_lista_espera          PRIMARY KEY (id),
    CONSTRAINT fk_espera_paciente       FOREIGN KEY (paciente_id)      REFERENCES pacientes (id),
    CONSTRAINT fk_espera_medico         FOREIGN KEY (medico_id)        REFERENCES medicos (id),
    CONSTRAINT fk_espera_especialidad   FOREIGN KEY (especialidad_id)  REFERENCES especialidades (id),
    CONSTRAINT fk_espera_oferta_medico  FOREIGN KEY (oferta_medico_id) REFERENCES medicos (id),
    CONSTRAINT fk_espera_cita           FOREIGN KEY (cita_id)          REFERENCES citas (id),
    CONSTRAINT ck_espera_fechas         CHECK (fecha_hasta >= fecha_desde),
    CONSTRAINT ck_espera_franja         CHECK (franja IN ('CUALQUIERA', 'MANANA', 'TARDE')),
    CONSTRAINT ck_espera_estado         CHECK (estado IN ('ACTIVA', 'OFERTADA', 'ACEPTADA', 'VENCIDA', 'CANCELADA'))
);
-- El orden de llegada (creado_en) define la prioridad dentro de cada especialidad.
CREATE INDEX ix_espera_especialidad ON lista_espera (especialidad_id, estado, creado_en);
CREATE INDEX ix_espera_paciente     ON lista_espera (paciente_id, estado);
-- Un paciente no puede tener dos solicitudes vigentes para la misma especialidad.
CREATE UNIQUE INDEX ux_espera_vigente ON lista_espera (paciente_id, especialidad_id)
    WHERE estado <> 'ACEPTADA' AND estado <> 'VENCIDA' AND estado <> 'CANCELADA';

-- ---------------------------------------------------------------------
-- Notificaciones y auditoria
-- ---------------------------------------------------------------------
CREATE TABLE notificaciones (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id          BIGINT               NULL,
    cita_id             BIGINT               NULL,
    destinatario        VARCHAR(150)         NOT NULL,
    tipo                VARCHAR(30)          NOT NULL,
    canal               VARCHAR(10)          NOT NULL CONSTRAINT df_notif_canal DEFAULT 'EMAIL',
    asunto              VARCHAR(200)         NOT NULL,
    cuerpo              VARCHAR(1000)        NULL,     -- sin datos clinicos
    estado              VARCHAR(12)          NOT NULL CONSTRAINT df_notif_estado DEFAULT 'PENDIENTE',
    intentos            INT                  NOT NULL CONSTRAINT df_notif_intentos DEFAULT 0,
    ultimo_error        VARCHAR(500)         NULL,
    clave_idempotencia  VARCHAR(120)         NOT NULL, -- evita notificaciones duplicadas
    programada_para     DATETIMEOFFSET(6)    NULL,
    enviada_en          DATETIMEOFFSET(6)    NULL,
    creado_en           DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_notif_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_notificaciones       PRIMARY KEY (id),
    CONSTRAINT uq_notif_idempotencia   UNIQUE (clave_idempotencia),
    CONSTRAINT fk_notif_usuario        FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_notif_cita           FOREIGN KEY (cita_id)    REFERENCES citas (id),
    CONSTRAINT ck_notif_intentos       CHECK (intentos >= 0),
    CONSTRAINT ck_notif_canal          CHECK (canal IN ('EMAIL')),
    CONSTRAINT ck_notif_estado         CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA', 'OMITIDA')),
    CONSTRAINT ck_notif_tipo           CHECK (tipo IN ('REGISTRO', 'CITA_RESERVADA', 'CITA_CONFIRMADA', 'CITA_REPROGRAMADA', 'CITA_CANCELADA', 'RECORDATORIO_CITA', 'RECUPERACION_PASSWORD', 'OFERTA_LISTA_ESPERA'))
);
CREATE INDEX ix_notif_estado ON notificaciones (estado, programada_para);
CREATE INDEX ix_notif_cita   ON notificaciones (cita_id);

CREATE TABLE auditoria (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id     BIGINT               NULL,      -- sin FK: la auditoria sobrevive a cualquier cambio de usuarios
    usuario_email  VARCHAR(150)         NULL,
    accion         VARCHAR(60)          NOT NULL,
    entidad        VARCHAR(60)          NOT NULL,
    recurso_id     VARCHAR(60)          NULL,
    resultado      VARCHAR(10)          NOT NULL,
    detalle        VARCHAR(500)         NULL,      -- informacion no sensible
    ip             VARCHAR(45)          NULL,
    creado_en      DATETIMEOFFSET(6)    NOT NULL CONSTRAINT df_auditoria_creado DEFAULT (CAST(SYSUTCDATETIME() AS DATETIMEOFFSET(6))),
    CONSTRAINT pk_auditoria       PRIMARY KEY (id),
    CONSTRAINT ck_auditoria_res   CHECK (resultado IN ('EXITO', 'FALLO', 'DENEGADO'))
);
CREATE INDEX ix_auditoria_fecha   ON auditoria (creado_en);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, recurso_id);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario_id, creado_en);
