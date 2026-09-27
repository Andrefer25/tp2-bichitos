-- SOLO PARA EL PROTOTIPO LOCAL. El inicio de App ejecuta este archivo en
-- bichitos_tp2 dentro de una transaccion: elimina los datos de prueba actuales
-- y repone un conjunto fijo. No usar con informacion real.
DELETE FROM auditoria_evento;
DELETE FROM accion_alerta;
DELETE FROM seguimiento;
DELETE FROM adopcion;
DELETE FROM decision;
DELETE FROM evaluacion_criterio;
DELETE FROM evaluacion_actividad;
DELETE FROM evaluacion;
DELETE FROM animal_criterio;
DELETE FROM criterio;
DELETE FROM verificacion;
DELETE FROM importacion_error;
DELETE FROM postulacion;
DELETE FROM importacion_lote;
DELETE FROM postulante;
DELETE FROM animal;
DELETE FROM hogar_transito;
DELETE FROM usuario;

INSERT INTO usuario(id,nombre,correo,rol,activo) VALUES
 (1,'Responsable de prueba','responsable@example.test','ADOPCIONES',TRUE),
 (2,'Coordinación de prueba','coordinacion@example.test','COORDINACION',TRUE);
INSERT INTO hogar_transito(id,nombre_referente,localidad,telefono,activo)
 VALUES (1,'Hogar de prueba','Vicente López','1100000000',TRUE);
INSERT INTO animal(id,nombre,especie,edad_aproximada_anios,necesidades_cuidado,estado,hogar_transito_id) VALUES
 (1,'Luna','Perro',3,'Patio seguro; adaptación gradual; compañía diaria.','APTO',1),
 (2,'Milo','Gato',2,'Ambiente seguro; adaptación tranquila; juego diario.','ADOPTADO',1),
 (3,'Mora','Gato',1,'Interior seguro; ventanas protegidas; adaptación tranquila.','APTO',1),
 (4,'Rocco','Perro',5,'Paseos diarios; actividad moderada; compañía y rutinas estables.','APTO',1),
 (5,'Nina','Perro',2,'Casa con espacio; socialización gradual; no dejar sola muchas horas.','EN_TRANSITO',1),
 (6,'Simba','Gato',4,'Ambiente interior; enriquecimiento; convivencia respetuosa con otros gatos.','APTO',1);
INSERT INTO criterio(id,nombre,descripcion,activo) VALUES
 (1,'Vivienda','Seguridad y compatibilidad del espacio',TRUE),
 (2,'Convivencia','Personas y otros animales',TRUE),
 (3,'Tiempo y recursos','Disponibilidad y capacidad de cuidado',TRUE),
 (4,'Compatibilidad con el animal','Necesidades concretas de la ficha',TRUE);
INSERT INTO animal_criterio(animal_id,criterio_id) VALUES
 (1,1),(1,2),(1,3),(1,4),(2,1),(2,2),(2,3),(2,4),
 (3,1),(3,2),(3,3),(3,4),(4,1),(4,2),(4,3),(4,4),
 (5,1),(5,2),(5,3),(5,4),(6,1),(6,2),(6,3),(6,4);
INSERT INTO importacion_lote(id,origen,archivo,inicio,fin,incorporadas,duplicadas,fallidas,usuario_id)
 VALUES (1,'CSV','demo_base.csv',NOW(),NOW(),7,0,0,1);
INSERT INTO postulante(id,tipo_documento,numero_documento,nombre,fecha_nacimiento,domicilio,localidad,telefono,correo) VALUES
 (1,'DNI','30000001','Ana Pérez','1990-05-12','Calle Uno 123','Vicente López','1100000001','ana@example.test'),
 (2,'DNI','30000002','Luis Gómez','2010-02-08','Calle Dos 25','Olivos','1100000002','luis@example.test'),
 (3,'DNI','30000003','María Ruiz','1988-11-15','Calle Tres 80','Florida','1100000003','maria@example.test'),
 (4,'DNI','31000001','Clara Soto','1991-03-04','Calle Norte 12','Olivos','1100000012','clara@example.test'),
 (5,'DNI','31000002','Diego Paz','1992-07-11','Calle Sur 88','Florida','1100000088','diego@example.test'),
 (6,'DNI','31000003','Eva Torres','1994-06-18','Calle Central 4','Munro','1100000006','eva@example.test');
INSERT INTO postulacion(id,animal_id,postulante_id,origen,id_origen,importacion_lote_id,alquila,consentimiento_propietario,tiempo_y_recursos,estado,motivo_estado) VALUES
 (1,2,1,'CSV','BASE001',1,FALSE,FALSE,TRUE,'APROBADA','Decisión fundada: vivienda y disponibilidad compatibles con Milo'),
 (2,1,2,'CSV','BASE002',1,FALSE,FALSE,TRUE,'NO_ELEGIBLE','La persona es menor de 21 años'),
 (3,1,3,'CSV','BASE003',1,TRUE,TRUE,TRUE,'RECIBIDA','Datos completos; verificar identidad, domicilio y consentimiento'),
 (4,1,4,'CSV','BASE004',1,FALSE,FALSE,TRUE,'EVALUADA','Evaluación cualitativa registrada; pendiente de decisión humana'),
 (5,1,5,'CSV','BASE005',1,TRUE,TRUE,TRUE,'EVALUADA','Evaluación cualitativa registrada; revisar seguridad de ventanas'),
 (6,1,6,'CSV','BASE006',1,FALSE,FALSE,TRUE,'RECIBIDA','Datos completos; verificar identidad y domicilio'),
 (7,3,4,'CSV','BASE007',1,FALSE,FALSE,TRUE,'RECIBIDA','Clara también se postuló para Mora; verificar identidad y domicilio');
INSERT INTO verificacion(id,postulacion_id,identidad_confirmada,domicilio_confirmado,consentimiento_confirmado,observaciones,usuario_id) VALUES
 (1,1,TRUE,TRUE,TRUE,'Documentación comprobada.',1),
 (2,4,TRUE,TRUE,TRUE,'Identidad y domicilio confirmados.',1),
 (3,5,TRUE,TRUE,TRUE,'Identidad, domicilio y consentimiento confirmados.',1);
INSERT INTO evaluacion(id,postulacion_id,entrevista,vivienda,encuentro,observaciones,usuario_id) VALUES
 (1,1,'FAVORABLE','FAVORABLE','FAVORABLE','Entrevista y encuentro compatibles con Milo.',1),
 (2,4,'FAVORABLE','FAVORABLE','FAVORABLE','Entrevista positiva; vivienda segura; encuentro tranquilo.',1),
 (3,5,'FAVORABLE','REVISAR','FAVORABLE','Revisar seguridad de ventanas antes de decidir.',1);
INSERT INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id) VALUES
 (1,'ENTREVISTA',CURRENT_DATE - INTERVAL 20 DAY,1),
 (1,'VIVIENDA',CURRENT_DATE - INTERVAL 19 DAY,1),
 (1,'ENCUENTRO',CURRENT_DATE - INTERVAL 18 DAY,1),
 (2,'ENTREVISTA',CURRENT_DATE - INTERVAL 4 DAY,1),
 (2,'VIVIENDA',CURRENT_DATE - INTERVAL 3 DAY,1),
 (2,'ENCUENTRO',CURRENT_DATE - INTERVAL 2 DAY,1),
 (3,'ENTREVISTA',CURRENT_DATE - INTERVAL 4 DAY,1),
 (3,'VIVIENDA',CURRENT_DATE - INTERVAL 3 DAY,1),
 (3,'ENCUENTRO',CURRENT_DATE - INTERVAL 2 DAY,1);
INSERT INTO evaluacion_criterio(evaluacion_id,criterio_id,valoracion,observaciones) VALUES
 (1,1,'FAVORABLE','Espacio seguro'),(1,2,'FAVORABLE','Convivencia adecuada'),
 (1,3,'FAVORABLE','Tiempo suficiente'),(1,4,'FAVORABLE','Compatible con Milo'),
 (2,1,'FAVORABLE','Espacio seguro'),(2,2,'FAVORABLE','Convivencia compatible'),
 (2,3,'FAVORABLE','Dispone de tiempo'),(2,4,'FAVORABLE','Compatible con Luna'),
 (3,1,'REVISAR','Falta red en una ventana'),(3,2,'FAVORABLE','Convivencia favorable'),
 (3,3,'FAVORABLE','Tiempo suficiente'),(3,4,'REVISAR','Revisar adaptación en el hogar');
INSERT INTO decision(id,postulacion_id,resultado,fundamento,usuario_id)
 VALUES (1,1,'APROBADA','La evaluación y las necesidades de Milo respaldan la adopción; decisión del equipo.',1);
INSERT INTO adopcion(id,decision_id,fecha_entrega,compromiso_firmado,estado)
 VALUES (1,1,CURRENT_DATE - INTERVAL 10 DAY,TRUE,'ACTIVA');
INSERT INTO seguimiento(id,adopcion_id,tipo,fecha_programada,fecha_limite,fecha_contacto,canal,observaciones,resultado,responsable_id) VALUES
 (1,1,'INICIAL',CURRENT_DATE - INTERVAL 8 DAY,CURRENT_DATE - INTERVAL 7 DAY,NOW() - INTERVAL 7 DAY,'WhatsApp','Se detectó necesidad de acompañamiento.','ALERTA',1),
 (2,1,'DIA15',CURRENT_DATE + INTERVAL 5 DAY,CURRENT_DATE + INTERVAL 6 DAY,NULL,NULL,NULL,NULL,1),
 (3,1,'DIA30',CURRENT_DATE + INTERVAL 20 DAY,CURRENT_DATE + INTERVAL 21 DAY,NULL,NULL,NULL,NULL,1),
 (4,1,'EXTRA',CURRENT_DATE - INTERVAL 1 DAY,CURRENT_DATE - INTERVAL 1 DAY,NULL,NULL,NULL,NULL,1);
INSERT INTO accion_alerta(id,seguimiento_id,motivo,accion,estado,fecha_apertura,fecha_limite,responsable_id)
 VALUES (1,1,'Dificultad de adaptación','Contactar a Ana y registrar evolución.','ABIERTA',NOW() - INTERVAL 7 DAY,CURRENT_DATE - INTERVAL 1 DAY,1);
INSERT INTO auditoria_evento(id,usuario_id,entidad,entidad_id,evento,detalle) VALUES
 (1,1,'decision',1,'DECISION_REGISTRADA','Aprobación fundada de Ana para Milo.'),
 (2,1,'adopcion',1,'ADOPCION_REGISTRADA','Entrega con compromiso y tres contactos programados.');
