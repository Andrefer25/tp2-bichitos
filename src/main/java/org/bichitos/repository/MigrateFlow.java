package org.bichitos.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Actualiza instalaciones previas sin borrar filas. Ejecutable de manera repetida. */
public final class MigrateFlow {
    public static void main(String[] args)throws Exception{
        try(Connection db=Database.open()){apply(db);}
    }
    public static void apply(Connection db)throws Exception{
        try(Statement st=db.createStatement()){
            st.execute("ALTER TABLE postulante MODIFY tipo_documento VARCHAR(15) NULL, MODIFY numero_documento VARCHAR(30) NULL, MODIFY nombre VARCHAR(120) NULL, MODIFY fecha_nacimiento DATE NULL, MODIFY domicilio VARCHAR(190) NULL, MODIFY localidad VARCHAR(120) NULL, MODIFY correo VARCHAR(190) NULL");
            if(!column(db,"postulacion","importacion_lote_id"))
                st.execute("ALTER TABLE postulacion ADD COLUMN importacion_lote_id BIGINT NULL AFTER id_origen");
            st.execute("ALTER TABLE postulacion MODIFY alquila BOOLEAN NULL, MODIFY consentimiento_propietario BOOLEAN NULL, MODIFY tiempo_y_recursos BOOLEAN NULL");
            if(!constraint(db,"fk_postulacion_lote"))
                st.execute("ALTER TABLE postulacion ADD CONSTRAINT fk_postulacion_lote FOREIGN KEY(importacion_lote_id) REFERENCES importacion_lote(id) ON DELETE RESTRICT");
            st.execute("ALTER TABLE verificacion MODIFY identidad_confirmada BOOLEAN NULL, MODIFY domicilio_confirmado BOOLEAN NULL, MODIFY consentimiento_confirmado BOOLEAN NULL");
            st.execute("CREATE TABLE IF NOT EXISTS animal_criterio (animal_id BIGINT NOT NULL,criterio_id BIGINT NOT NULL,PRIMARY KEY(animal_id,criterio_id),CONSTRAINT fk_ac_animal FOREIGN KEY(animal_id) REFERENCES animal(id) ON DELETE RESTRICT,CONSTRAINT fk_ac_criterio FOREIGN KEY(criterio_id) REFERENCES criterio(id) ON DELETE RESTRICT) ENGINE=InnoDB");
            st.execute("CREATE TABLE IF NOT EXISTS evaluacion_actividad (evaluacion_id BIGINT NOT NULL,tipo ENUM('ENTREVISTA','VIVIENDA','ENCUENTRO') NOT NULL,fecha DATE NOT NULL,responsable_id BIGINT NOT NULL,PRIMARY KEY(evaluacion_id,tipo),CONSTRAINT fk_ea_evaluacion FOREIGN KEY(evaluacion_id) REFERENCES evaluacion(id) ON DELETE RESTRICT,CONSTRAINT fk_ea_usuario FOREIGN KEY(responsable_id) REFERENCES usuario(id) ON DELETE RESTRICT) ENGINE=InnoDB");
            for(String type:new String[]{"ENTREVISTA","VIVIENDA","ENCUENTRO"})
                st.executeUpdate("INSERT IGNORE INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id) SELECT e.id,'"+type+"',DATE(e.fecha),e.usuario_id FROM evaluacion e");
            st.executeUpdate("INSERT IGNORE INTO animal_criterio(animal_id,criterio_id) SELECT a.id,c.id FROM animal a CROSS JOIN criterio c WHERE c.activo=TRUE");
            if(!column(db,"accion_alerta","fecha_apertura"))
                st.execute("ALTER TABLE accion_alerta ADD COLUMN fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER estado");
            st.executeUpdate("UPDATE postulacion p JOIN decision d ON d.postulacion_id=p.id SET p.motivo_estado=CONCAT('Decisión fundada: ',LEFT(d.fundamento,470)) WHERE p.estado IN ('APROBADA','RECHAZADA','EN_ESPERA') AND d.id=(SELECT MAX(x.id) FROM decision x WHERE x.postulacion_id=p.id)");
            System.out.println("Migración del flujo aplicada; no se eliminaron registros.");
        }
    }
    private static boolean column(Connection db,String table,String name)throws Exception{
        try(ResultSet rs=db.getMetaData().getColumns(db.getCatalog(),null,table,name)){return rs.next();}
    }
    private static boolean constraint(Connection db,String name)throws Exception{
        try(var st=db.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME=?")){
            st.setString(1,name);try(ResultSet rs=st.executeQuery()){rs.next();return rs.getInt(1)>0;}
        }
    }
}
