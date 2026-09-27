-- Aplicar una sola vez sobre una instalación previa. No borra datos.
USE bichitos_tp2;
ALTER TABLE postulante
 MODIFY tipo_documento VARCHAR(15) NULL,
 MODIFY numero_documento VARCHAR(30) NULL,
 MODIFY nombre VARCHAR(120) NULL,
 MODIFY fecha_nacimiento DATE NULL,
 MODIFY domicilio VARCHAR(190) NULL,
 MODIFY localidad VARCHAR(120) NULL,
 MODIFY correo VARCHAR(190) NULL;
ALTER TABLE postulacion
 ADD COLUMN importacion_lote_id BIGINT NULL AFTER id_origen,
 MODIFY alquila BOOLEAN NULL,
 MODIFY consentimiento_propietario BOOLEAN NULL,
 MODIFY tiempo_y_recursos BOOLEAN NULL,
 ADD CONSTRAINT fk_postulacion_lote FOREIGN KEY (importacion_lote_id)
 REFERENCES importacion_lote(id) ON DELETE RESTRICT;
ALTER TABLE verificacion
 MODIFY identidad_confirmada BOOLEAN NULL,
 MODIFY domicilio_confirmado BOOLEAN NULL,
 MODIFY consentimiento_confirmado BOOLEAN NULL;
CREATE TABLE IF NOT EXISTS animal_criterio (
 animal_id BIGINT NOT NULL, criterio_id BIGINT NOT NULL,
 PRIMARY KEY (animal_id,criterio_id),
 CONSTRAINT fk_ac_animal FOREIGN KEY (animal_id) REFERENCES animal(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ac_criterio FOREIGN KEY (criterio_id) REFERENCES criterio(id) ON DELETE RESTRICT
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
INSERT IGNORE INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id)
 SELECT e.id,'ENTREVISTA',DATE(e.fecha),e.usuario_id FROM evaluacion e;
INSERT IGNORE INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id)
 SELECT e.id,'VIVIENDA',DATE(e.fecha),e.usuario_id FROM evaluacion e;
INSERT IGNORE INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id)
 SELECT e.id,'ENCUENTRO',DATE(e.fecha),e.usuario_id FROM evaluacion e;
INSERT IGNORE INTO animal_criterio(animal_id,criterio_id)
 SELECT a.id,c.id FROM animal a CROSS JOIN criterio c WHERE c.activo=TRUE;
ALTER TABLE accion_alerta
 ADD COLUMN fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER estado;
UPDATE postulacion p JOIN decision d ON d.postulacion_id=p.id
 SET p.motivo_estado=CONCAT('Decisión fundada: ',LEFT(d.fundamento,470))
 WHERE p.estado IN ('APROBADA','RECHAZADA','EN_ESPERA')
   AND d.id=(SELECT MAX(x.id) FROM decision x WHERE x.postulacion_id=p.id);
-- Las postulaciones anteriores permanecen válidas y sin lote asociado.
