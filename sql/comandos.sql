-- Ejemplos de SQL para MySQL 8. Ejecutar desde la raíz del proyecto o Workbench.
-- GET/POST/PUT/DELETE son métodos HTTP; aquí se corresponden con
-- SELECT/INSERT/UPDATE/DELETE. La aplicación aplica reglas de negocio,
-- verificación y auditoría que no se ejecutan al modificar SQL directamente.
-- Los ejemplos de escritura terminan en ROLLBACK: no cambian la demo.

USE bichitos_tp2;

-- GET: animales disponibles para adopción.
SELECT id, nombre, especie, estado, necesidades_cuidado
FROM animal
WHERE estado = 'APTO'
ORDER BY nombre, id;

-- GET: postulaciones de un animal con nombre y estado de verificación.
SET @animal_id = (SELECT id FROM animal WHERE nombre = 'Luna' ORDER BY id LIMIT 1);
SELECT p.id AS postulacion_id, a.nombre AS animal, t.nombre AS postulante,
       p.estado, v.identidad_confirmada, v.domicilio_confirmado,
       v.consentimiento_confirmado
FROM postulacion p
JOIN animal a ON a.id = p.animal_id
JOIN postulante t ON t.id = p.postulante_id
LEFT JOIN verificacion v ON v.postulacion_id = p.id
WHERE p.animal_id = @animal_id
ORDER BY p.id;

-- GET: una misma persona puede tener expedientes independientes por animal.
SELECT t.nombre, a.nombre AS animal, p.id AS postulacion_id, p.estado
FROM postulacion p
JOIN postulante t ON t.id = p.postulante_id
JOIN animal a ON a.id = p.animal_id
WHERE t.tipo_documento = 'DNI' AND t.numero_documento = '31000001'
ORDER BY p.id;

-- POST/INSERT: crear un hogar de tránsito ficticio dentro de una transacción.
START TRANSACTION;
INSERT INTO hogar_transito (nombre_referente, localidad, telefono, activo)
VALUES ('Hogar SQL temporal', 'Olivos', '1100000099', TRUE);
SET @hogar_temporal_id = LAST_INSERT_ID();
SELECT id, nombre_referente, activo
FROM hogar_transito WHERE id = @hogar_temporal_id;

-- PUT/UPDATE: cambiar el estado activo del registro recién creado.
UPDATE hogar_transito SET activo = FALSE
WHERE id = @hogar_temporal_id AND nombre_referente = 'Hogar SQL temporal';
SELECT id, activo FROM hogar_transito WHERE id = @hogar_temporal_id;

-- DELETE: borrar únicamente el registro temporal de este ejemplo.
DELETE FROM hogar_transito
WHERE id = @hogar_temporal_id AND nombre_referente = 'Hogar SQL temporal';
ROLLBACK;

-- PUT/UPDATE de estado de una postulación: ejemplo didáctico sobre la demo.
-- En el uso normal, cambiar estados desde la aplicación para respetar las
-- precondiciones y registrar la auditoría correspondiente.
START TRANSACTION;
SET @postulacion_demo_id = (
  SELECT id FROM postulacion WHERE origen = 'CSV' AND id_origen = 'BASE003' LIMIT 1
);
SELECT id, estado FROM postulacion WHERE id = @postulacion_demo_id;
UPDATE postulacion SET estado = 'EN_ESPERA', motivo_estado = 'Cambio temporal de ejemplo SQL'
WHERE id = @postulacion_demo_id AND estado = 'RECIBIDA';
SELECT id, estado, motivo_estado FROM postulacion WHERE id = @postulacion_demo_id;
ROLLBACK;

-- GET: comprobar que el estado original se conservó tras ROLLBACK.
SELECT id, estado, motivo_estado
FROM postulacion WHERE origen = 'CSV' AND id_origen = 'BASE003';
