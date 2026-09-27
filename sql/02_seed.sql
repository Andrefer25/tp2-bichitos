USE bichitos_tp2;
INSERT INTO usuario(id,nombre,correo,rol) VALUES
 (1,'Responsable de prueba','responsable@example.test','ADOPCIONES'),
 (2,'Coordinación de prueba','coordinacion@example.test','COORDINACION')
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre);
INSERT INTO hogar_transito(id,nombre_referente,localidad,telefono)
 VALUES (1,'Hogar de prueba','Vicente López','1100000000')
ON DUPLICATE KEY UPDATE nombre_referente=VALUES(nombre_referente);
INSERT INTO animal(id,nombre,especie,edad_aproximada_anios,necesidades_cuidado,estado,hogar_transito_id)
 VALUES (1,'Luna','Perro',3,'Patio seguro; adaptación gradual; compañía diaria.','APTO',1)
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre);
INSERT INTO animal(id,nombre,especie,edad_aproximada_anios,necesidades_cuidado,estado,hogar_transito_id)
 VALUES (2,'Milo','Gato',2,'Ambiente seguro; adaptación tranquila; juego diario.','APTO',1)
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre);
INSERT INTO criterio(id,nombre,descripcion) VALUES
 (1,'Vivienda','Seguridad y compatibilidad del espacio'),
 (2,'Convivencia','Personas y otros animales'),
 (3,'Tiempo y recursos','Disponibilidad y capacidad de cuidado'),
 (4,'Compatibilidad con el animal','Necesidades concretas de la ficha')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);
INSERT IGNORE INTO animal_criterio(animal_id,criterio_id)
 SELECT a.id,c.id FROM animal a CROSS JOIN criterio c
 WHERE a.id IN (1,2) AND c.activo=TRUE;
