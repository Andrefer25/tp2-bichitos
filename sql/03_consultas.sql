USE bichitos_tp2;
-- Inserción controlada: el seed y el CSV son las inserciones de demostración.
INSERT INTO hogar_transito(nombre_referente,localidad,telefono)
VALUES ('Hogar SQL de prueba','Olivos','1100000099');
SELECT LAST_INSERT_ID() AS hogar_creado;
-- Candidatos con estado y verificación, sin convertir la evaluación en puntaje.
SELECT a.nombre AS animal, p.id AS postulacion, t.nombre AS postulante,
       p.estado, p.motivo_estado, v.identidad_confirmada, v.domicilio_confirmado
FROM postulacion p JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
LEFT JOIN verificacion v ON v.postulacion_id=p.id
WHERE a.id=1 ORDER BY p.fecha,p.id;
-- Historial de decisiones con fundamento.
SELECT d.id,d.fecha,d.resultado,d.fundamento,u.nombre AS responsable
FROM decision d JOIN usuario u ON u.id=d.usuario_id
JOIN postulacion p ON p.id=d.postulacion_id
WHERE p.animal_id=1 ORDER BY d.fecha,d.id;
-- Seguimientos vencidos y alertas abiertas.
SELECT s.id,s.tipo,s.fecha_limite,a.id AS adopcion
FROM seguimiento s JOIN adopcion a ON a.id=s.adopcion_id
WHERE s.fecha_contacto IS NULL AND s.fecha_limite<CURRENT_DATE();
SELECT al.id,al.motivo,al.accion,al.fecha_limite
FROM accion_alerta al WHERE al.estado='ABIERTA' ORDER BY al.fecha_limite;
