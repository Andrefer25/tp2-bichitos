# Evidencia de pruebas ejecutadas el 26/09/2026

Entorno: JDK 16.0.2, Connector/J 8.0.33, MySQL 8.0.40 en `localhost`, esquema exclusivo `bichitos_tp2`. Credenciales tomadas de variables de usuario de Windows y no impresas ni guardadas.

| Prueba | Entrada | Resultado observado |
|---|---|---|
| Compilación | `javac -encoding UTF-8` sobre `src/main/java` y `src/test/java` | Exit 0 |
| Unitarias | `org.bichitos.SmokeTest` | 6/6 correctas |
| Conexión | Inicio de `org.bichitos.web.App` | `MySQL conectado. Animales: 1`; HTTP GET 200 |
| CSV inicial | `postulaciones_ejemplo.csv` | Lote 1: 4 incorporadas, 0 duplicadas, 0 fallidas |
| CSV repetido | Mismo archivo | Lote 2: 0 incorporadas, 4 duplicadas, 0 fallidas |
| CSV con error | `postulaciones_errores.csv` | Lote 3: 0 incorporadas, 1 duplicada, 1 fallida; fila 1 registrada en `importacion_error` |
| Edad | Postulación 2, persona menor de 21 | `NO_ELEGIBLE` |
| Consentimiento | Postulación 3, alquiler sin consentimiento | `PENDIENTE` |
| Elegibilidad | Postulaciones 1 y 4 con datos confirmados | `ELEGIBLE` |
| Evaluación | Postulación 1, entrevista, vivienda, encuentro y dos criterios favorables | HTTP 200; evaluación #3 creada |
| Decisión | Postulación 1, aprobación con fundamento | HTTP 200; decisión #1 creada |
| Entrega | Decisión #1, compromiso firmado, 26/09/2026 | Adopción #1; seguimientos 28-29/09, 11/10 y 26/10 |
| Negativas | Evaluar no elegible, decidir sin fundamento, adoptar sin firma | HTTP 400 en los tres casos |
| Alerta | Seguimiento inicial #1 con resultado ALERTA | HTTP 200; se crea acción y seguimiento EXTRA |
| Cierre | Cerrar alerta #1 con acción registrada | HTTP 200 |
| FK | Insertar seguimiento con adopción 999999 | Error MySQL 1452; no se inserta |
| SELECT/INSERT | `03_consultas.sql` | Exit 0; se creó hogar ficticio #2 y las consultas devolvieron datos |
| DELETE seguro | `04_delete_seguro.sql` | `filas_borradas=1`; quedaron 0 filas con el marcador de prueba |
| Script de control | `05_pruebas.sql` | Exit 0; 4 postulaciones, 4 seguimientos para la adopción #1, ninguna clave de origen duplicada y 19 FK listadas |

No se ejecutaron: integración real con Google Sheets, autenticación/roles, HTTPS, respaldo/restauración, pruebas de 20 usuarios ni despliegue remoto. Son elementos de la solución final y no deben presentarse como implementados.

## Ampliación del flujo integral

Se aplicó `06_migracion_flujo.sql` mediante `MigrateFlow` sin eliminar registros; una segunda ejecución del migrador no duplicó tablas ni columnas. Se cargó el segundo animal ficticio, Milo. La nueva interfaz se ejecutó en el puerto local 8081 para no interferir con la instancia anterior en 8080.

| Prueba | Resultado observado |
|---|---|
| CSV mixto seleccionado desde la página | Lote 8: 3 incorporadas, 1 pendiente, 1 duplicada y 1 fallida. El error de animal inexistente quedó visible por fila. |
| Revisión de lote | Enlaces a los expedientes 5 Clara Soto, 6 Diego Paz y 7 Eva; cada uno conserva el vínculo con el lote. |
| Completar solicitud pendiente | Expediente 7 pasó de PENDIENTE a RECIBIDA tras completar datos corregibles. |
| Elegibilidad | Expedientes 5 y 6 pasaron a ELEGIBLE. El intento de reverificar la postulación 1 ya aprobada devolvió HTTP 400 sin regresión de estado. |
| Comparación cualitativa | Clara y Diego aparecen simultáneamente para Milo; ambos tienen evaluación. La vivienda de Diego figura Revisar y no hay puntuación automática. |
| Selección de evaluación | GET `/?postulacion=6&animal=2#evaluaciones` devolvió `data-postulacion="6"`, tarjeta seleccionada y formulario «Evaluación de Diego Paz». El formulario solo envía ese expediente; el enlace de la otra tarjeta cambia la selección. |
| Decisión y entrega | La aprobación fundada de Clara creó decisión 2 y adopción 2 con tres contactos. El intento de aprobar simultáneamente a Diego para el mismo animal fue rechazado con HTTP 400. |
| Seguimiento y alerta | Un contacto de adopción 2 con resultado ALERTA creó un contacto extraordinario y acción 2; el cierre de la acción quedó registrado. |
| Restricción de animal no apto | La evaluación del expediente 4 después de la adopción del animal devolvió HTTP 400; no se alteró la evaluación. |

En aquella ampliación no se verificó un guardado nuevo con las tres fechas porque los dos animales de esa sesión ya habían sido adoptados. Tras la rehidratación posterior, Luna quedó apta y esa comprobación se realizó sobre el expediente 5: HTTP 200 y retorno a su evaluación, con fechas, responsable y criterios guardados.

## Rehidratación automática y consultas

La versión siguiente incorporó `DemoDatabase.initialize()` al arranque de `App`, `sql/07_rehidratar_demo.sql` y consultas de solo lectura en `sql/08_consultas_demo.sql`. La rutina de reinicio rechaza hosts remotos y esquemas distintos de `bichitos_tp2`; no se ejecuta al abrir una ruta web ni al refrescar el navegador, sino al iniciar el proceso Java.

| Prueba | Resultado observado |
|---|---|
| Compilación posterior al cambio | `javac` exit 0; `SmokeTest`: 6/6. |
| Primer arranque con reinicio activo | Se rehidrató `bichitos_tp2`; 2 animales y 6 solicitudes base. |
| Mutación entre arranques | Carga de `postulaciones_flujo_prueba.csv`: lote 9, 4 incorporadas, 1 pendiente, 1 fallida. |
| Segundo arranque | El lote 9 y sus solicitudes desaparecieron; quedaron nuevamente las 6 solicitudes de `demo_base.csv`, con 2 pendientes. |
| Consultas de solo lectura | `SqlRunner sql/08_consultas_demo.sql` devolvió solicitudes, requisitos, comparación Clara–Diego, fechas de actividades, adopción de Ana, cuatro contactos, alerta abierta, lote y auditoría. |
| Evaluación con fechas tras rehidratación | POST `/evaluate` del expediente 5 con entrevista, vivienda, encuentro, tres fechas y cuatro criterios: HTTP 200; vuelve al mismo expediente. |

El reinicio elimina deliberadamente los cambios ficticios de la sesión anterior. Para conservar datos de prueba entre ejecuciones se puede usar `BICHITOS_DEMO_RESET=false`; no se debe activar el reinicio en un entorno con datos reales.

## Ajuste: postulaciones completas

La nueva versión exige datos obligatorios completos en cada fila CSV. La verificación documental y la evaluación siguen siendo acciones del equipo; la corrección manual de datos declarados se retiró de la interfaz y del servidor. El informe Word no se modificó.

| Prueba | Resultado observado |
|---|---|
| Compilación y pruebas unitarias | `javac` exit 0; `SmokeTest`: 6/6. |
| CSV mixto | 5 incorporadas, 1 duplicada y 2 rechazadas. `FLUJO06` registró falta de tipo de documento y fecha de nacimiento; `FLUJO04`, animal inexistente. |
| Expediente con varios animales | Clara aparece para Luna y Mora; GET de ambos expedientes devuelve HTTP 200 y permite navegar entre ellos. |
| Edición manual retirada | En el HTML no aparece `action='/complete'`; POST `/complete` devuelve HTTP 400. |
| Verificación y evaluación | POST `/verify` de Clara para Mora la dejó ELEGIBLE; POST `/evaluate` con tres fechas y cuatro criterios registró la evaluación. Su expediente de Luna permaneció EVALUADA. |
| Restauración de demo | Se inició la versión nueva en 8080 con 6 animales y 7 postulaciones completas; GET del expediente de Mora devolvió HTTP 200 con verificación disponible. |

## Revisión previa a publicar en GitHub (27/09/2026)

La prueba integral anterior detectó dos defectos de admisión: se aceptaban postulaciones para Milo ya adoptado y se podía repetir la pareja Clara–Mora usando otro `id_origen`. Se corrigió `CsvImporter`: exige animal `APTO` y clasifica como duplicada una postulación existente de la misma persona para el mismo animal, incluso si cambia el identificador de origen. El CSV de flujo se actualizó para usar animales aptos y parejas persona–animal no repetidas. Las cifras y capturas de secciones anteriores son resultados históricos de la versión previa, no el resultado esperado al ejecutar el CSV actualizado.

| Prueba | Resultado observado |
|---|---|
| Compilación | `javac -encoding UTF-8` sobre `src/main/java` y `src/test/java`: exit 0. |
| Unitarias | `SmokeTest`: 6/6. |
| Regresión MySQL | `ImporterIntegrationTest`: duplicado Clara–Luna y animal Milo `ADOPTADO` rechazados; lote temporal eliminado por la prueba. |
| Reinicio seguro | El valor predeterminado de `BICHITOS_DEMO_RESET` cambió a `false`. Para instalar la demo en un esquema local limpio, se activa `true` de forma explícita solo en el primer arranque. |

La base local conserva filas importadas antes de la corrección; no se borraron ni se migraron automáticamente. Para reproducir el conteo esperado del CSV actualizado (5/1/2), partir de la demo limpia con consentimiento de descartar los registros locales previos.
