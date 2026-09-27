# TP2 Bichitos Zona Norte

Prototipo operacional del flujo de adopción y seguimiento. El caso es ficticio; los datos de ejemplo también. La interfaz se sirve solo en `127.0.0.1:8080` por HTTP local. El despliegue HTTPS y la sincronización automática con Google Sheets son parte del diseño final, **no** del prototipo.

## Requisitos

- JDK 16 o superior (IntelliJ IDEA es opcional).
- MySQL 8 en ejecución y una cuenta con permisos sobre `bichitos_tp2`.
- Connector/J 8.0.33, incluido en `lib/mysql-connector-j-8.0.33.jar` (el JAR ya estaba descargado en el material de trabajo).

## Preparación

1. Abrir PowerShell en esta carpeta (`tp2-bichitos`). Comprobar `java -version`, `javac -version` y que el servicio MySQL esté iniciado.
2. En MySQL Workbench o en el cliente `mysql`, ejecutar como administrador (cambiar la contraseña de ejemplo):

   ```sql
   CREATE DATABASE IF NOT EXISTS bichitos_tp2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER IF NOT EXISTS 'bichitos'@'localhost' IDENTIFIED BY 'CAMBIAR_POR_UNA_CONTRASENA_LOCAL';
   GRANT ALL PRIVILEGES ON bichitos_tp2.* TO 'bichitos'@'localhost';
   ```

   Si el usuario ya existe, definir o comprobar su contraseña por separado. También se puede usar una cuenta MySQL existente con permisos equivalentes. No ejecutar este SQL contra un servidor con datos que deban conservarse.
3. Si no existe `.env`, ejecutar `Copy-Item .env.example .env`. Configurar allí `BICHITOS_DB_URL=jdbc:mysql://localhost:3306/bichitos_tp2?serverTimezone=America%2FArgentina%2FBuenos_Aires`, `BICHITOS_DB_USER=bichitos`, `BICHITOS_DB_PASSWORD=` con la contraseña elegida. `Database` lee `.env` desde el directorio de trabajo; las variables del entorno tienen prioridad. `.env` está excluido de Git: no compartirlo ni mostrar su contenido en capturas.
4. Para **crear la demo por primera vez**, cambiar temporalmente `BICHITOS_DEMO_RESET=true` en `.env`, compilar y ejecutar con los comandos de la sección siguiente. Ese inicio crea las tablas mediante `sql/01_schema.sql` y carga seis animales y siete postulaciones ficticias; Clara figura para Luna y Mora. Después de confirmar el arranque, detener con `Ctrl+C` y volver a `BICHITOS_DEMO_RESET=false` para conservar los cambios siguientes. No hace falta ejecutar `sql/02_seed.sql`.
5. En los arranques normales, `BICHITOS_DEMO_RESET=false` no modifica ni migra automáticamente la base: exige que el esquema ya exista. Abrir `http://127.0.0.1:8080` y detener con `Ctrl+C`. Para cambiar el puerto, definir por ejemplo `$env:BICHITOS_PORT='8081'` antes de ejecutar Java.
6. **Precaución:** cada arranque con `BICHITOS_DEMO_RESET=true` elimina los registros previos de `bichitos_tp2` y repone los datos ficticios. Hacer copia externa de todo dato local que deba conservarse antes de activarlo. El reinicio solo admite el esquema `bichitos_tp2` en MySQL local; no usar con datos reales.
7. En **Importación**, seleccionar `data/postulaciones_flujo_prueba.csv`. Incluye cinco solicitudes nuevas para animales aptos, una fila incompleta, un duplicado por origen y un animal inexistente. En una demo limpia el resultado esperado es **5 incorporadas, 1 duplicada y 2 rechazadas**. No se admite una segunda solicitud de la misma persona para el mismo animal ni animales que no estén `APTO`. Seguir el flujo **verificar identidad, domicilio y consentimiento → evaluar por animal → decidir → registrar adopción → hacer seguimientos**. No hay formulario para completar datos ni alta manual de animales.

Si se parte de una instalación anterior, el arranque con `BICHITOS_DEMO_RESET=true` detecta las columnas/tablas del flujo faltantes y aplica la migración antes de rehidratar, **pero descarta sus registros**. Con `false` no se aplica ninguna migración automática: preparar el esquema por separado si corresponde. Hacer una copia externa de los registros que se deban conservar antes de migrar o reiniciar.

## Compilación en PowerShell

```powershell
New-Item -ItemType Directory -Force out | Out-Null
$sources = Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName
javac -encoding UTF-8 -d out $sources
java -cp "out;lib/mysql-connector-j-8.0.33.jar" org.bichitos.web.App
```

En IntelliJ IDEA, abrir esta carpeta, seleccionar JDK 16 o superior, comprobar que `src/main/java` sea Sources Root y que el módulo `main` tenga la biblioteca `lib/mysql-connector-j-8.0.33.jar`. Crear una configuración **Application** con clase principal `org.bichitos.web.App`, módulo `main` y directorio de trabajo igual a la raíz de `tp2-bichitos`. La configuración de ejecución no viene compartida en el repositorio.

Para ejecutar las pruebas unitarias sin MySQL, desde la misma carpeta:

```powershell
$sources = Get-ChildItem src/main/java,src/test/java -Recurse -Filter *.java | ForEach-Object FullName
javac -encoding UTF-8 -d out $sources
java -cp "out;lib/mysql-connector-j-8.0.33.jar" org.bichitos.SmokeTest
```

Con la demo cargada, la regresión de importación contra MySQL comprueba que un duplicado persona-animal y un animal adoptado no se incorporan; elimina únicamente el lote temporal que crea:

```powershell
java -cp "out;lib/mysql-connector-j-8.0.33.jar" org.bichitos.ImporterIntegrationTest
```

`sql/08_consultas_demo.sql` contiene SELECT de solo lectura para solicitudes, requisitos, comparación, actividades, adopciones, seguimientos, lotes y auditoría. Se pueden ejecutar desde la consola SQL de IntelliJ o con:

```powershell
java '-Dfile.encoding=UTF-8' -cp "out;lib/mysql-connector-j-8.0.33.jar" org.bichitos.repository.SqlRunner sql/08_consultas_demo.sql
```

`sql/03_consultas.sql` conserva el ejemplo original de INSERT y SELECT. `sql/04_delete_seguro.sql` demuestra DELETE solo sobre una fila temporal identificada. `sql/05_pruebas.sql` incluye comprobaciones de integridad. `sql/01_schema.sql` y `sql/02_seed.sql` quedan disponibles para instalación manual, pero el inicio normal ya ejecuta el esquema y la rehidratación automáticamente.

`sql/comandos.sql` reúne ejemplos de GET/SELECT, POST/INSERT, PUT/UPDATE de estado y DELETE. Las operaciones de escritura se ejecutan en transacciones que terminan con `ROLLBACK`, por lo que no persisten cambios. Puede probarse con `java -cp "out;lib/mysql-connector-j-8.0.33.jar" org.bichitos.repository.SqlRunner sql/comandos.sql`. Es material didáctico: los cambios de estado reales deben pasar por la aplicación para respetar sus validaciones y auditoría.

## Alcance y seguridad

La importación CSV actúa como contingencia de Google Sheets y evita duplicados por `(origen, id_origen)` y por pareja `(postulante, animal)` en el servicio. Las filas con datos obligatorios faltantes, sin identificador de origen, con animal inexistente o no apto se rechazan y registran como errores del lote; la corrección se realiza en el origen y se vuelve a importar. La base histórica puede conservar postulaciones duplicadas anteriores a esta validación; esta versión no las borra automáticamente. Las postulaciones completas pasan por verificación humana antes de evaluar. La decisión es humana y exige fundamento. Este ajuste del prototipo simplifica el tratamiento de datos incompletos previsto en el TP1. La versión local no implementa autenticación, autorización, TLS, copias de seguridad, mediciones de veinte usuarios ni API de Google Sheets: los RNF correspondientes permanecen diseñados y pendientes. El prototipo no se debe publicar como servicio real ni usar con datos personales reales.

## Interfaz web

La interfaz es una SPA liviana, servida por el mismo `HttpServer` de Java y construida con HTML, CSS y JavaScript sin framework. La navegación lateral separa Resumen, Postulaciones, Evaluaciones, Adopciones, Seguimientos e Importación. Los formularios usan servicios Java y muestran errores en su propio contexto, conservando lo escrito. Las acciones exitosas redirigen al expediente o lote pertinente para evitar repetir operaciones al refrescar. Los recursos se encuentran en `src/main/resources/web/`. El diseño adapta sus columnas para pantallas pequeñas y conserva el alcance de prototipo local.
