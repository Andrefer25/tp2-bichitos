package org.bichitos;

import org.bichitos.application.CsvImporter;
import org.bichitos.repository.Database;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/** Regresión contra la demo local; elimina únicamente el lote creado por esta prueba. */
public final class ImporterIntegrationTest {
    private static final String HEADER =
            "id_origen;animal_id;tipo_documento;numero_documento;nombre;fecha_nacimiento;domicilio;localidad;telefono;correo;alquila;consentimiento_propietario;tiempo_y_recursos\n";

    public static void main(String[] args) throws Exception {
        String id = UUID.randomUUID().toString();
        String csv = HEADER
                + id + "-LUNA;1;DNI;31000001;Clara Soto;1991-03-04;Calle Norte 12;Olivos;1100000012;clara@example.test;no;no;si\n"
                + id + "-MILO;2;DNI;30000001;Ana Pérez;1990-05-12;Calle Uno 123;Vicente López;1100000001;ana@example.test;no;no;si\n";
        CsvImporter.Summary result = new CsvImporter().importReader(
                new StringReader(csv), "regresion_importador.csv", 1);
        try {
            if (result.incorporated() != 0 || result.duplicates() != 1 || result.failed() != 1)
                throw new AssertionError("Esperado 0 nuevas, 1 duplicada persona-animal y 1 animal no apto; obtenido: "
                        + result + "; errores: " + errors(result.batchId()));
            System.out.println("OK: duplicado persona-animal y animal adoptado rechazados");
        } finally {
            try (Connection db = Database.open()) {
                db.setAutoCommit(false);
                try (PreparedStatement posts = db.prepareStatement("DELETE FROM postulacion WHERE importacion_lote_id=?");
                     PreparedStatement errors = db.prepareStatement("DELETE FROM importacion_error WHERE lote_id=?");
                     PreparedStatement batch = db.prepareStatement("DELETE FROM importacion_lote WHERE id=?")) {
                    posts.setLong(1, result.batchId());
                    posts.executeUpdate();
                    errors.setLong(1, result.batchId());
                    errors.executeUpdate();
                    batch.setLong(1, result.batchId());
                    batch.executeUpdate();
                    db.commit();
                } catch (Exception error) {
                    db.rollback();
                    throw error;
                }
            }
        }
    }

    private static String errors(long batchId) throws Exception {
        StringBuilder details = new StringBuilder();
        try (Connection db = Database.open();
             PreparedStatement query = db.prepareStatement("SELECT numero_fila,detalle FROM importacion_error WHERE lote_id=? ORDER BY numero_fila")) {
            query.setLong(1, batchId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) details.append(rows.getInt(1)).append(':').append(rows.getString(2)).append(';');
            }
        }
        return details.toString();
    }
}
