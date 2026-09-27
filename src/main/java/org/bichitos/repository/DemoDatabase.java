package org.bichitos.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Reinicio intencional de datos ficticios; exclusivamente en MySQL local/bichitos_tp2. */
public final class DemoDatabase {
    private DemoDatabase() { }

    public static void initialize() throws Exception {
        String option=Database.setting("BICHITOS_DEMO_RESET","false");
        if ("false".equalsIgnoreCase(option)) {
            System.out.println("Rehidratación desactivada; se conservan los registros existentes.");
            return;
        }
        if (!"true".equalsIgnoreCase(option))
            throw new IllegalArgumentException("BICHITOS_DEMO_RESET debe ser true o false.");

        // La conexión sin esquema permite crear bichitos_tp2 si todavía no existe.
        // openServerForDemo impide aplicar este reinicio a un host remoto u otro esquema.
        try (Connection server=Database.openServerForDemo()) {
            SqlRunner.execute(server,Path.of("sql","01_schema.sql"));
        }
        try (Connection db=Database.open()) {
            try (Statement st=db.createStatement(); ResultSet rs=st.executeQuery("SELECT DATABASE()")) {
                if (!rs.next() || !"bichitos_tp2".equals(rs.getString(1)))
                    throw new IllegalStateException("La base activa no es bichitos_tp2; se canceló el reinicio.");
            }
            if (needsMigration(db)) MigrateFlow.apply(db);
            db.setAutoCommit(false);
            try {
                SqlRunner.execute(db,Path.of("sql","07_rehidratar_demo.sql"));
                db.commit();
            } catch (Exception error) {
                db.rollback();
                throw error;
            }
        }
        System.out.println("Datos ficticios rehidratados en bichitos_tp2. Los cambios del inicio anterior se descartaron.");
    }

    private static boolean needsMigration(Connection db) throws Exception {
        return !column(db,"postulacion","importacion_lote_id")
                || !column(db,"accion_alerta","fecha_apertura")
                || !table(db,"animal_criterio")
                || !table(db,"evaluacion_actividad");
    }

    private static boolean column(Connection db,String table,String name) throws Exception {
        try (ResultSet rs=db.getMetaData().getColumns("bichitos_tp2",null,table,name)) {
            return rs.next();
        }
    }

    private static boolean table(Connection db,String name) throws Exception {
        try (ResultSet rs=db.getMetaData().getTables("bichitos_tp2",null,name,new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
