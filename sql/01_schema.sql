CREATE DATABASE IF NOT EXISTS bichitos_tp2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE bichitos_tp2;

CREATE TABLE IF NOT EXISTS usuario (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre VARCHAR(120) NOT NULL,
 correo VARCHAR(190) NOT NULL UNIQUE,
 rol ENUM('COORDINACION','ADOPCIONES','TRANSITO','SEGUIMIENTO') NOT NULL,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hogar_transito (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre_referente VARCHAR(120) NOT NULL,
 localidad VARCHAR(120) NOT NULL, telefono VARCHAR(40), activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS animal (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre VARCHAR(100) NOT NULL,
 especie VARCHAR(40) NOT NULL, edad_aproximada_anios TINYINT UNSIGNED,
 necesidades_cuidado TEXT NOT NULL,
 estado ENUM('EN_TRANSITO','APTO','ADOPTADO','NO_APTO') NOT NULL,
 hogar_transito_id BIGINT NULL,
 CONSTRAINT fk_animal_hogar FOREIGN KEY (hogar_transito_id) REFERENCES hogar_transito(id) ON DELETE RESTRICT,
 INDEX idx_animal_estado (estado)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS postulante (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 tipo_documento VARCHAR(15) NULL, numero_documento VARCHAR(30) NULL,
 nombre VARCHAR(120) NULL, fecha_nacimiento DATE NULL,
 domicilio VARCHAR(190) NULL, localidad VARCHAR(120) NULL,
 telefono VARCHAR(40), correo VARCHAR(190) NULL,
 UNIQUE KEY uq_postulante_documento (tipo_documento, numero_documento)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS importacion_lote (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, origen VARCHAR(30) NOT NULL,
 archivo VARCHAR(255) NOT NULL, inicio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 fin DATETIME NULL, incorporadas INT UNSIGNED NOT NULL DEFAULT 0,
 duplicadas INT UNSIGNED NOT NULL DEFAULT 0, fallidas INT UNSIGNED NOT NULL DEFAULT 0,
 usuario_id BIGINT NULL,
 CONSTRAINT fk_lote_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS postulacion (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, animal_id BIGINT NOT NULL,
 postulante_id BIGINT NOT NULL, origen VARCHAR(30) NOT NULL,
 id_origen VARCHAR(100) NOT NULL, importacion_lote_id BIGINT NULL,
 fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 alquila BOOLEAN NULL, consentimiento_propietario BOOLEAN NULL,
 tiempo_y_recursos BOOLEAN NULL,
 estado ENUM('RECIBIDA','PENDIENTE','NO_ELEGIBLE','ELEGIBLE','EVALUADA','APROBADA','RECHAZADA','EN_ESPERA') NOT NULL DEFAULT 'RECIBIDA',
 motivo_estado VARCHAR(500),
 CONSTRAINT fk_postulacion_animal FOREIGN KEY (animal_id) REFERENCES animal(id) ON DELETE RESTRICT,
 CONSTRAINT fk_postulacion_persona FOREIGN KEY (postulante_id) REFERENCES postulante(id) ON DELETE RESTRICT,
 CONSTRAINT fk_postulacion_lote FOREIGN KEY (importacion_lote_id) REFERENCES importacion_lote(id) ON DELETE RESTRICT,
 UNIQUE KEY uq_postulacion_origen (origen, id_origen),
 INDEX idx_postulacion_animal_estado (animal_id, estado)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS importacion_error (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, lote_id BIGINT NOT NULL,
 numero_fila INT UNSIGNED NOT NULL, id_origen VARCHAR(100), detalle VARCHAR(500) NOT NULL,
 CONSTRAINT fk_error_lote FOREIGN KEY (lote_id) REFERENCES importacion_lote(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS verificacion (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, postulacion_id BIGINT NOT NULL UNIQUE,
 identidad_confirmada BOOLEAN NULL, domicilio_confirmado BOOLEAN NULL,
 consentimiento_confirmado BOOLEAN NULL, observaciones VARCHAR(500),
 fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, usuario_id BIGINT NOT NULL,
 CONSTRAINT fk_verif_post FOREIGN KEY (postulacion_id) REFERENCES postulacion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_verif_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evaluacion (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, postulacion_id BIGINT NOT NULL UNIQUE,
 entrevista ENUM('FAVORABLE','REVISAR','DESFAVORABLE') NOT NULL,
 vivienda ENUM('FAVORABLE','REVISAR','DESFAVORABLE') NOT NULL,
 encuentro ENUM('FAVORABLE','REVISAR','DESFAVORABLE') NOT NULL,
 observaciones TEXT NOT NULL,
 fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, usuario_id BIGINT NOT NULL,
 CONSTRAINT fk_eval_post FOREIGN KEY (postulacion_id) REFERENCES postulacion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_eval_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evaluacion_actividad (
 evaluacion_id BIGINT NOT NULL,
 tipo ENUM('ENTREVISTA','VIVIENDA','ENCUENTRO') NOT NULL,
 fecha DATE NOT NULL,
 responsable_id BIGINT NOT NULL,
 PRIMARY KEY (evaluacion_id,tipo),
 CONSTRAINT fk_ea_evaluacion FOREIGN KEY (evaluacion_id) REFERENCES evaluacion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ea_usuario FOREIGN KEY (responsable_id) REFERENCES usuario(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS criterio (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre VARCHAR(100) NOT NULL UNIQUE,
 descripcion VARCHAR(300) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS animal_criterio (
 animal_id BIGINT NOT NULL, criterio_id BIGINT NOT NULL,
 PRIMARY KEY (animal_id,criterio_id),
 CONSTRAINT fk_ac_animal FOREIGN KEY (animal_id) REFERENCES animal(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ac_criterio FOREIGN KEY (criterio_id) REFERENCES criterio(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evaluacion_criterio (
 evaluacion_id BIGINT NOT NULL, criterio_id BIGINT NOT NULL,
 valoracion ENUM('FAVORABLE','REVISAR','DESFAVORABLE','NO_APLICA') NOT NULL,
 observaciones VARCHAR(500) NOT NULL,
 PRIMARY KEY (evaluacion_id, criterio_id),
 CONSTRAINT fk_ec_eval FOREIGN KEY (evaluacion_id) REFERENCES evaluacion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ec_criterio FOREIGN KEY (criterio_id) REFERENCES criterio(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS decision (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, postulacion_id BIGINT NOT NULL,
 resultado ENUM('APROBADA','RECHAZADA','EN_ESPERA') NOT NULL,
 fundamento TEXT NOT NULL, fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 usuario_id BIGINT NOT NULL,
 CONSTRAINT fk_dec_post FOREIGN KEY (postulacion_id) REFERENCES postulacion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_dec_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
 INDEX idx_dec_post_fecha (postulacion_id, fecha)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS adopcion (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, decision_id BIGINT NOT NULL UNIQUE,
 fecha_entrega DATE NOT NULL, compromiso_firmado BOOLEAN NOT NULL,
 estado ENUM('ACTIVA','DEVOLUCION_PENDIENTE','CERRADA','DEVUELTA') NOT NULL DEFAULT 'ACTIVA',
 CONSTRAINT chk_compromiso CHECK (compromiso_firmado = TRUE),
 CONSTRAINT fk_adop_dec FOREIGN KEY (decision_id) REFERENCES decision(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS seguimiento (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, adopcion_id BIGINT NOT NULL,
 tipo ENUM('INICIAL','DIA15','DIA30','EXTRA') NOT NULL,
 fecha_programada DATE NOT NULL, fecha_limite DATE NOT NULL,
 fecha_contacto DATETIME NULL, canal VARCHAR(60), observaciones TEXT,
 resultado ENUM('SATISFACTORIO','ACOMPANAMIENTO','ALERTA','POSIBLE_DEVOLUCION','SIN_RESPUESTA') NULL,
 responsable_id BIGINT NULL,
 CONSTRAINT chk_fechas_seguimiento CHECK (fecha_limite >= fecha_programada),
 CONSTRAINT fk_seg_adop FOREIGN KEY (adopcion_id) REFERENCES adopcion(id) ON DELETE RESTRICT,
 CONSTRAINT fk_seg_usuario FOREIGN KEY (responsable_id) REFERENCES usuario(id) ON DELETE RESTRICT,
 UNIQUE KEY uq_seguimiento_plan (adopcion_id,tipo,fecha_programada),
 INDEX idx_seg_pendiente (fecha_contacto,fecha_limite)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accion_alerta (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, seguimiento_id BIGINT NOT NULL,
 motivo VARCHAR(500) NOT NULL, accion VARCHAR(500) NOT NULL,
 estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
 fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 fecha_limite DATE NOT NULL, fecha_cierre DATETIME NULL,
 responsable_id BIGINT NOT NULL,
 CONSTRAINT fk_alerta_seg FOREIGN KEY (seguimiento_id) REFERENCES seguimiento(id) ON DELETE RESTRICT,
 CONSTRAINT fk_alerta_usuario FOREIGN KEY (responsable_id) REFERENCES usuario(id) ON DELETE RESTRICT,
 INDEX idx_alerta_estado_limite (estado,fecha_limite)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auditoria_evento (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, usuario_id BIGINT NULL,
 entidad VARCHAR(50) NOT NULL, entidad_id BIGINT NOT NULL,
 evento VARCHAR(80) NOT NULL, detalle VARCHAR(500) NOT NULL,
 fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_audit_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
 INDEX idx_audit_entidad (entidad,entidad_id,fecha)
) ENGINE=InnoDB;
