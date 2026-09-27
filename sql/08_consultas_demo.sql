-- Consultas de solo lectura del recorrido. Ejecutar con SqlRunner o IntelliJ.
-- 1. Bandeja de solicitudes: persona, animal, estado y motivo.
SELECT p.id AS solicitud, a.nombre AS animal, t.nombre AS postulante,
       p.estado, p.motivo_estado, l.archivo AS lote
FROM postulacion p
JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
LEFT JOIN importacion_lote l ON l.id=p.importacion_lote_id
ORDER BY p.id;

-- 2. Verificación de requisitos declarados y comprobados.
SELECT p.id AS solicitud, t.nombre AS postulante, t.fecha_nacimiento,
       p.alquila, p.consentimiento_propietario, p.tiempo_y_recursos,
       v.identidad_confirmada, v.domicilio_confirmado,
       v.consentimiento_confirmado, p.estado
FROM postulacion p
JOIN postulante t ON t.id=p.postulante_id
LEFT JOIN verificacion v ON v.postulacion_id=p.id
ORDER BY p.id;

-- 3. Comparación cualitativa de candidaturas por animal, sin puntaje.
SELECT a.nombre AS animal, p.id AS solicitud, t.nombre AS postulante,
       e.entrevista, e.vivienda, e.encuentro, c.nombre AS criterio,
       ec.valoracion, ec.observaciones
FROM evaluacion e
JOIN postulacion p ON p.id=e.postulacion_id
JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
JOIN evaluacion_criterio ec ON ec.evaluacion_id=e.id
JOIN criterio c ON c.id=ec.criterio_id
ORDER BY a.id,p.id,c.id;

-- 4. Actividades con fecha y responsable.
SELECT a.nombre AS animal, t.nombre AS postulante, ea.tipo,
       ea.fecha, u.nombre AS responsable
FROM evaluacion_actividad ea
JOIN evaluacion e ON e.id=ea.evaluacion_id
JOIN postulacion p ON p.id=e.postulacion_id
JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
JOIN usuario u ON u.id=ea.responsable_id
ORDER BY p.id,ea.fecha;

-- 5. Decisión, adopción y agenda de seguimiento.
SELECT a.nombre AS animal, t.nombre AS adoptante, d.resultado,
       d.fundamento, ad.fecha_entrega, s.tipo, s.fecha_programada,
       s.fecha_limite, s.fecha_contacto, s.resultado AS resultado_contacto
FROM decision d
JOIN postulacion p ON p.id=d.postulacion_id
JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
LEFT JOIN adopcion ad ON ad.decision_id=d.id
LEFT JOIN seguimiento s ON s.adopcion_id=ad.id
ORDER BY d.id,s.fecha_programada;

-- 6. Seguimientos pendientes/vencidos y acciones abiertas.
SELECT s.id AS seguimiento, a.nombre AS animal, t.nombre AS adoptante,
       s.tipo, s.fecha_limite,
       CASE WHEN al.id IS NOT NULL THEN 'ALERTA ABIERTA'
            WHEN s.fecha_limite<CURRENT_DATE THEN 'VENCIDO'
            ELSE 'PENDIENTE' END AS prioridad,
       al.id AS alerta, al.accion, al.estado AS estado_alerta
FROM seguimiento s
JOIN adopcion ad ON ad.id=s.adopcion_id
JOIN decision d ON d.id=ad.decision_id
JOIN postulacion p ON p.id=d.postulacion_id
JOIN animal a ON a.id=p.animal_id
JOIN postulante t ON t.id=p.postulante_id
LEFT JOIN accion_alerta al ON al.seguimiento_id=s.id AND al.estado='ABIERTA'
WHERE s.fecha_contacto IS NULL OR al.id IS NOT NULL
ORDER BY CASE WHEN al.id IS NOT NULL THEN 0
              WHEN s.fecha_limite<CURRENT_DATE THEN 1 ELSE 2 END,
         s.fecha_limite;

-- 7. Resumen del lote y errores de importación.
SELECT l.id AS lote, l.archivo, l.incorporadas, l.duplicadas,
       l.fallidas, COUNT(p.id) AS solicitudes_vinculadas,
       SUM(CASE WHEN p.estado IN ('RECIBIDA','PENDIENTE') THEN 1 ELSE 0 END) AS por_verificar
FROM importacion_lote l
LEFT JOIN postulacion p ON p.importacion_lote_id=l.id
GROUP BY l.id,l.archivo,l.incorporadas,l.duplicadas,l.fallidas
ORDER BY l.id;
SELECT e.lote_id, e.numero_fila, e.id_origen, e.detalle
FROM importacion_error e ORDER BY e.lote_id,e.numero_fila;

-- 8. Historial auditable del caso.
SELECT entidad, entidad_id, evento, detalle, fecha
FROM auditoria_evento ORDER BY fecha,id;
