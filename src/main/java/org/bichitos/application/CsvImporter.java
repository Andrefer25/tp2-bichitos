package org.bichitos.application;

import org.bichitos.repository.Database;
import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

public final class CsvImporter {
    public record Summary(int incorporated, int duplicates, int failed, long batchId) { }
    private static final String HEADER =
            "id_origen;animal_id;tipo_documento;numero_documento;nombre;fecha_nacimiento;domicilio;localidad;telefono;correo;alquila;consentimiento_propietario;tiempo_y_recursos";

    public Summary importFile(Path path, long userId) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) > 2_000_000)
            throw new IllegalArgumentException("El CSV debe existir y no superar 2 MB.");
        try (Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            return importReader(reader,path.getFileName().toString(),userId);
        }
    }

    public Summary importReader(Reader source,String filename,long userId) throws Exception {
        try (BufferedReader reader=new BufferedReader(source); Connection db=Database.open()) {
            String header=reader.readLine();
            if (header==null || !header.replace("\uFEFF","").equals(HEADER))
                throw new IllegalArgumentException("Encabezado CSV distinto del formato esperado.");
            long batch;
            try (PreparedStatement st=db.prepareStatement(
                    "INSERT INTO importacion_lote(origen,archivo,usuario_id) VALUES ('CSV',?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1,filename); st.setLong(2,userId); st.executeUpdate();
                try(ResultSet keys=st.getGeneratedKeys()){keys.next();batch=keys.getLong(1);}
            }
            int added=0,duplicate=0,failed=0,row=0;
            String line;
            while((line=reader.readLine())!=null) {
                row++;
                if(line.isBlank())continue;
                try {
                    List<String> v=parse(line);
                    if(v.size()!=13)throw new IllegalArgumentException("Se esperaban 13 columnas");
                    String origin=v.get(0).trim();
                    if(origin.isBlank())throw new IllegalArgumentException("Falta id_origen; no se puede evitar duplicados");
                    if(exists(db,origin)){duplicate++;continue;}
                    long animal=Long.parseLong(v.get(1));
                    String animalState=animalState(db,animal);
                    if(animalState==null)throw new IllegalArgumentException("Animal inexistente: "+animal);
                    if(!"APTO".equals(animalState))
                        throw new IllegalArgumentException("Animal no disponible para adopción: "+animalState);
                    LocalDate birth=date(v.get(5));
                    Boolean rents=yes(v.get(10)),consent=yes(v.get(11)),care=yes(v.get(12));
                    List<String> missing=new ArrayList<>();
                    String[] names={"tipo de documento","número de documento","nombre","fecha de nacimiento","domicilio","localidad","correo"};
                    int[] fields={2,3,4,5,6,7,9};
                    for(int i=0;i<fields.length;i++)if(v.get(fields[i]).isBlank())missing.add(names[i]);
                    if(rents==null)missing.add("si alquila");
                    if(care==null)missing.add("tiempo y recursos");
                    if(Boolean.TRUE.equals(rents)&&consent==null)missing.add("consentimiento del propietario");
                    if(!missing.isEmpty())throw new IllegalArgumentException("Faltan datos obligatorios: "+String.join(", ",missing));
                    String state="RECIBIDA",reason="Lista para verificar identidad y domicilio";
                    if(Period.between(birth,LocalDate.now()).getYears()<21){
                        state="NO_ELEGIBLE";reason="La persona es menor de 21 años";
                    }else if(!care){state="NO_ELEGIBLE";reason="No declara tiempo y recursos suficientes";}
                    else if(Boolean.TRUE.equals(rents)&&!Boolean.TRUE.equals(consent)){
                        state="PENDIENTE";reason="Falta consentimiento del propietario";
                    }
                    db.setAutoCommit(false);
                    long person=upsertPerson(db,v,birth);
                    if(alreadyApplied(db,person,animal)){
                        db.rollback();duplicate++;continue;
                    }
                    try(PreparedStatement st=db.prepareStatement(
                            "INSERT INTO postulacion(animal_id,postulante_id,origen,id_origen,importacion_lote_id,alquila,consentimiento_propietario,tiempo_y_recursos,estado,motivo_estado) VALUES (?,?,'CSV',?,?,?,?,?,?,?)")){
                        st.setLong(1,animal);st.setLong(2,person);st.setString(3,origin);st.setLong(4,batch);
                        nullableBoolean(st,5,rents);nullableBoolean(st,6,consent);nullableBoolean(st,7,care);
                        st.setString(8,state);st.setString(9,reason);st.executeUpdate();
                    }
                    db.commit();added++;
                }catch(Exception error){
                    if(!db.getAutoCommit())db.rollback();
                    failed++;
                    try(PreparedStatement st=db.prepareStatement(
                            "INSERT INTO importacion_error(lote_id,numero_fila,id_origen,detalle) VALUES (?,?,?,?)")){
                        st.setLong(1,batch);st.setInt(2,row);
                        st.setString(3,line.split(";",-1)[0]);
                        st.setString(4,limit(error.getMessage(),500));st.executeUpdate();
                    }
                }finally{db.setAutoCommit(true);}
            }
            try(PreparedStatement st=db.prepareStatement(
                    "UPDATE importacion_lote SET fin=NOW(),incorporadas=?,duplicadas=?,fallidas=? WHERE id=?")){
                st.setInt(1,added);st.setInt(2,duplicate);st.setInt(3,failed);st.setLong(4,batch);st.executeUpdate();
            }
            return new Summary(added,duplicate,failed,batch);
        }
    }

    private static void nullableBoolean(PreparedStatement st,int i,Boolean value)throws SQLException{
        if(value==null)st.setNull(i,Types.BOOLEAN);else st.setBoolean(i,value);
    }
    private static LocalDate date(String value){
        if(value.isBlank())return null;
        LocalDate date=LocalDate.parse(value);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Fecha de nacimiento futura");
        return date;
    }
    private static Boolean yes(String value){
        if(value.isBlank())return null;
        if(value.equalsIgnoreCase("si")||value.equalsIgnoreCase("sí"))return true;
        if(value.equalsIgnoreCase("no"))return false;
        throw new IllegalArgumentException("Valor booleano distinto de si/no: "+value);
    }
    private static String limit(String s,int n){return s==null?"Error desconocido":s.substring(0,Math.min(n,s.length()));}
    private static boolean exists(Connection db,String origin)throws SQLException{
        try(PreparedStatement st=db.prepareStatement("SELECT id FROM postulacion WHERE origen='CSV' AND id_origen=?")){
            st.setString(1,origin);try(ResultSet rs=st.executeQuery()){return rs.next();}
        }
    }
    private static String animalState(Connection db,long animal)throws SQLException{
        try(PreparedStatement st=db.prepareStatement("SELECT estado FROM animal WHERE id=?")){
            st.setLong(1,animal);
            try(ResultSet rs=st.executeQuery()){return rs.next()?rs.getString(1):null;}
        }
    }
    private static boolean alreadyApplied(Connection db,long person,long animal)throws SQLException{
        try(PreparedStatement st=db.prepareStatement(
                "SELECT 1 FROM postulacion WHERE postulante_id=? AND animal_id=? LIMIT 1")){
            st.setLong(1,person);st.setLong(2,animal);
            try(ResultSet rs=st.executeQuery()){return rs.next();}
        }
    }
    private static long upsertPerson(Connection db,List<String> v,LocalDate birth)throws SQLException{
        try(PreparedStatement st=db.prepareStatement(
                "INSERT INTO postulante(tipo_documento,numero_documento,nombre,fecha_nacimiento,domicilio,localidad,telefono,correo) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),nombre=COALESCE(VALUES(nombre),nombre),fecha_nacimiento=COALESCE(VALUES(fecha_nacimiento),fecha_nacimiento),domicilio=COALESCE(VALUES(domicilio),domicilio),localidad=COALESCE(VALUES(localidad),localidad),telefono=COALESCE(VALUES(telefono),telefono),correo=COALESCE(VALUES(correo),correo)",Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<3;i++)st.setString(i+1,blankNull(v.get(new int[]{2,3,4}[i])));
            if(birth==null)st.setNull(4,Types.DATE);else st.setDate(4,Date.valueOf(birth));
            for(int i=5;i<=8;i++)st.setString(i,blankNull(v.get(new int[]{6,7,8,9}[i-5])));
            st.executeUpdate();
            try(ResultSet keys=st.getGeneratedKeys()){if(keys.next())return keys.getLong(1);}
        }
        throw new SQLException("No se obtuvo el identificador del postulante");
    }
    private static String blankNull(String value){return value.isBlank()?null:value;}
    public static List<String> parse(String line){
        List<String> fields=new ArrayList<>();StringBuilder current=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){current.append('"');i++;}else quoted=!quoted;}
            else if(c==';'&&!quoted){fields.add(current.toString().trim());current.setLength(0);}
            else current.append(c);
        }
        if(quoted)throw new IllegalArgumentException("Comillas CSV sin cerrar");
        fields.add(current.toString().trim());return fields;
    }
}
