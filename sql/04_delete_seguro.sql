USE bichitos_tp2;
-- No ejecutar contra datos reales. Crea y borra una fila temporal identificada.
START TRANSACTION;
INSERT INTO hogar_transito(nombre_referente,localidad)
VALUES ('DELETE_DEMO_TP2_2026','TEST');
SET @demo_id := LAST_INSERT_ID();
SELECT id,nombre_referente FROM hogar_transito WHERE id=@demo_id;
DELETE FROM hogar_transito
WHERE id=@demo_id AND nombre_referente='DELETE_DEMO_TP2_2026';
SELECT ROW_COUNT() AS filas_borradas;
COMMIT;
