USE bichitos_tp2;
SELECT COUNT(*) AS postulaciones FROM postulacion;
SELECT origen,id_origen,COUNT(*) AS cantidad FROM postulacion
GROUP BY origen,id_origen HAVING COUNT(*)>1;
SELECT estado,COUNT(*) AS cantidad FROM postulacion GROUP BY estado;
SELECT a.id AS adopcion,COUNT(s.id) AS seguimientos
FROM adopcion a LEFT JOIN seguimiento s ON s.adopcion_id=a.id
GROUP BY a.id;
SELECT TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA='bichitos_tp2' AND REFERENCED_TABLE_NAME IS NOT NULL;
