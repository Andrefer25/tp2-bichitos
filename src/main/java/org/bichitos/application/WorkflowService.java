package org.bichitos.application;

import org.bichitos.domain.Eligibility;
import org.bichitos.repository.Database;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WorkflowService {
    public record CriterionValue(String value,String notes) { }

    public Eligibility.Result verify(long postId, boolean identity, boolean address,
                                     boolean landlordConsent, long userId) throws Exception {
        return verify(postId,Boolean.valueOf(identity),Boolean.valueOf(address),
                Boolean.valueOf(landlordConsent),userId);
    }

    public Eligibility.Result verify(long postId, Boolean identity, Boolean address,
                                     Boolean landlordConsent, long userId) throws Exception {
        try (Connection db=Database.open()) {
            db.setAutoCommit(false);
            try {
                LocalDate birth; Boolean rents, care, declaredConsent; String state;
                List<String> missing=new ArrayList<>();
                try (PreparedStatement st=db.prepareStatement(
                        "SELECT t.fecha_nacimiento,p.alquila,p.tiempo_y_recursos,p.consentimiento_propietario,p.estado,t.tipo_documento,t.numero_documento,t.nombre,t.domicilio,t.localidad,t.correo FROM postulacion p JOIN postulante t ON t.id=p.postulante_id WHERE p.id=? FOR UPDATE")) {
                    st.setLong(1,postId);
                    try (ResultSet rs=st.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Postulación inexistente");
                        birth=rs.getDate(1)==null?null:rs.getDate(1).toLocalDate();
                        rents=(Boolean)rs.getObject(2);care=(Boolean)rs.getObject(3);
                        declaredConsent=(Boolean)rs.getObject(4);state=rs.getString(5);
                        String[] names={"tipo de documento","número de documento","nombre","domicilio","localidad","correo"};
                        for(int i=0;i<names.length;i++)if(rs.getString(i+6)==null||rs.getString(i+6).isBlank())missing.add(names[i]);
                    }
                }
                if(!state.equals("RECIBIDA")&&!state.equals("PENDIENTE")&&!state.equals("ELEGIBLE"))
                    throw new IllegalStateException("La postulación ya avanzó de etapa; no se puede modificar su elegibilidad");
                if(birth==null)missing.add("fecha de nacimiento");
                if(rents==null)missing.add("si alquila");
                if(care==null)missing.add("tiempo y recursos");
                if(Boolean.TRUE.equals(rents)&&declaredConsent==null)missing.add("consentimiento del propietario");
                Eligibility.Result result;
                if(!missing.isEmpty())result=new Eligibility.Result("PENDIENTE","Falta completar: "+String.join(", ",missing));
                else result=Eligibility.assess(birth,identity,address,rents,
                        Boolean.TRUE.equals(declaredConsent)?landlordConsent:Boolean.FALSE,care);
                try (PreparedStatement st=db.prepareStatement(
                        "INSERT INTO verificacion(postulacion_id,identidad_confirmada,domicilio_confirmado,consentimiento_confirmado,usuario_id) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE identidad_confirmada=VALUES(identidad_confirmada),domicilio_confirmado=VALUES(domicilio_confirmado),consentimiento_confirmado=VALUES(consentimiento_confirmado),fecha=NOW(),usuario_id=VALUES(usuario_id)")) {
                    st.setLong(1,postId);setBoolean(st,2,identity);setBoolean(st,3,address);
                    setBoolean(st,4,landlordConsent);st.setLong(5,userId);st.executeUpdate();
                }
                try (PreparedStatement st=db.prepareStatement("UPDATE postulacion SET estado=?,motivo_estado=? WHERE id=?")) {
                    st.setString(1,result.state());st.setString(2,result.reason());st.setLong(3,postId);st.executeUpdate();
                }
                audit(db,userId,"postulacion",postId,"VERIFICACION",result.state()+": "+result.reason());
                db.commit();return result;
            } catch(Exception e) {db.rollback();throw e;}
        }
    }

    public long evaluate(long postId,String interview,String home,String meeting,String notes,
                         String criterionValues,long userId) throws Exception {
        Map<Long,CriterionValue> parsed=new java.util.LinkedHashMap<>();
        if(criterionValues!=null&&!criterionValues.isBlank())for(String entry:criterionValues.split("\\|")){
            String[] parts=entry.split("[=:]",3);
            if(parts.length!=3)throw new IllegalArgumentException("Criterio inválido: "+entry);
            parsed.put(Long.parseLong(parts[0]),new CriterionValue(parts[1],parts[2]));
        }
        return evaluate(postId,interview,home,meeting,notes,parsed,userId);
    }

    public long evaluate(long postId,String interview,String home,String meeting,String notes,
                         Map<Long,CriterionValue> criterionValues,long userId) throws Exception {
        return evaluate(postId,interview,home,meeting,notes,criterionValues,
                Map.of("ENTREVISTA",LocalDate.now(),"VIVIENDA",LocalDate.now(),"ENCUENTRO",LocalDate.now()),userId);
    }

    public long evaluate(long postId,String interview,String home,String meeting,String notes,
                         Map<Long,CriterionValue> criterionValues,Map<String,LocalDate> activityDates,
                         long userId) throws Exception {
        requireChoice(interview);requireChoice(home);requireChoice(meeting);
        if (notes==null || notes.isBlank()) throw new IllegalArgumentException("La evaluación requiere observaciones");
        if (criterionValues==null || criterionValues.isEmpty()) throw new IllegalArgumentException("La evaluación requiere al menos un criterio cualitativo");
        for(String type:new String[]{"ENTREVISTA","VIVIENDA","ENCUENTRO"})
            if(activityDates==null||activityDates.get(type)==null||activityDates.get(type).isAfter(LocalDate.now()))
                throw new IllegalArgumentException("Fecha inválida para "+type.toLowerCase());
        try (Connection db=Database.open()) {
            db.setAutoCommit(false);
            try {
                String state,animalState;long animalId;
                try (PreparedStatement st=db.prepareStatement("SELECT p.estado,p.animal_id,a.estado FROM postulacion p JOIN animal a ON a.id=p.animal_id WHERE p.id=? FOR UPDATE")) {
                    st.setLong(1,postId);try(ResultSet rs=st.executeQuery()) {if(!rs.next())throw new IllegalArgumentException("Postulación inexistente");state=rs.getString(1);animalId=rs.getLong(2);animalState=rs.getString(3);}
                }
                if (!state.equals("ELEGIBLE") && !state.equals("EVALUADA")) throw new IllegalStateException("Solo se evalúan postulaciones elegibles");
                if(!animalState.equals("APTO"))throw new IllegalStateException("El animal no está apto para evaluar candidaturas");
                List<Long> expected=new ArrayList<>();
                try(PreparedStatement st=db.prepareStatement("SELECT c.id FROM criterio c JOIN animal_criterio ac ON ac.criterio_id=c.id WHERE ac.animal_id=? AND c.activo=TRUE")){
                    st.setLong(1,animalId);try(ResultSet rs=st.executeQuery()){while(rs.next())expected.add(rs.getLong(1));}
                }
                if(expected.isEmpty()||criterionValues.size()!=expected.size()||!criterionValues.keySet().containsAll(expected))
                    throw new IllegalArgumentException("Evaluá todos los criterios aplicables al animal");
                long evalId;
                try (PreparedStatement st=db.prepareStatement(
                        "INSERT INTO evaluacion(postulacion_id,entrevista,vivienda,encuentro,observaciones,usuario_id) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),entrevista=VALUES(entrevista),vivienda=VALUES(vivienda),encuentro=VALUES(encuentro),observaciones=VALUES(observaciones),fecha=NOW(),usuario_id=VALUES(usuario_id)",Statement.RETURN_GENERATED_KEYS)) {
                    st.setLong(1,postId);st.setString(2,interview);st.setString(3,home);st.setString(4,meeting);st.setString(5,notes);st.setLong(6,userId);st.executeUpdate();
                    try(ResultSet rs=st.getGeneratedKeys()){if(!rs.next())throw new SQLException("Sin id de evaluación");evalId=rs.getLong(1);}
                }
                for(var entry:criterionValues.entrySet()) {
                    long criterion=entry.getKey();String value=entry.getValue().value();String criterionNotes=entry.getValue().notes();
                    if(!value.equals("FAVORABLE")&&!value.equals("REVISAR")&&!value.equals("DESFAVORABLE")&&!value.equals("NO_APLICA"))throw new IllegalArgumentException("Valoración inválida");
                    if(criterionNotes==null||criterionNotes.isBlank())throw new IllegalArgumentException("Falta observación del criterio");
                    try(PreparedStatement st=db.prepareStatement(
                            "INSERT INTO evaluacion_criterio(evaluacion_id,criterio_id,valoracion,observaciones) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE valoracion=VALUES(valoracion),observaciones=VALUES(observaciones)")) {
                        st.setLong(1,evalId);st.setLong(2,criterion);st.setString(3,value);st.setString(4,criterionNotes);st.executeUpdate();
                    }
                }
                for(String type:new String[]{"ENTREVISTA","VIVIENDA","ENCUENTRO"}){
                    try(PreparedStatement st=db.prepareStatement("INSERT INTO evaluacion_actividad(evaluacion_id,tipo,fecha,responsable_id) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE fecha=VALUES(fecha),responsable_id=VALUES(responsable_id)")){
                        st.setLong(1,evalId);st.setString(2,type);st.setDate(3,Date.valueOf(activityDates.get(type)));
                        st.setLong(4,userId);st.executeUpdate();
                    }
                }
                try(PreparedStatement st=db.prepareStatement("UPDATE postulacion SET estado='EVALUADA' WHERE id=?")){st.setLong(1,postId);st.executeUpdate();}
                audit(db,userId,"evaluacion",evalId,"EVALUACION","Evaluación cualitativa registrada");
                db.commit();return evalId;
            } catch(Exception e){db.rollback();throw e;}
        }
    }

    public long decide(long postId,String result,String reason,long userId) throws Exception {
        if(!result.equals("APROBADA")&&!result.equals("RECHAZADA")&&!result.equals("EN_ESPERA"))throw new IllegalArgumentException("Resultado inválido");
        if(reason==null||reason.isBlank())throw new IllegalArgumentException("La decisión requiere fundamento");
        try(Connection db=Database.open()) {
            db.setAutoCommit(false);
            try {
                long animalId;String state,animalState;
                try(PreparedStatement st=db.prepareStatement("SELECT p.animal_id,p.estado,a.estado FROM postulacion p JOIN animal a ON a.id=p.animal_id WHERE p.id=? FOR UPDATE")){
                    st.setLong(1,postId);try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Postulación inexistente");animalId=rs.getLong(1);state=rs.getString(2);animalState=rs.getString(3);}
                }
                if(!state.equals("EVALUADA")&&!state.equals("EN_ESPERA"))
                    throw new IllegalStateException("La postulación debe estar evaluada para decidir");
                if(result.equals("APROBADA")) {
                    if(!state.equals("EVALUADA")&&!state.equals("EN_ESPERA"))throw new IllegalStateException("Debe completar la evaluación antes de aprobar");
                    if(!animalState.equals("APTO"))throw new IllegalStateException("El animal no está apto");
                    try(PreparedStatement st=db.prepareStatement("SELECT COUNT(*) FROM decision d JOIN postulacion p ON p.id=d.postulacion_id WHERE p.animal_id=? AND d.resultado='APROBADA'")){
                        st.setLong(1,animalId);try(ResultSet rs=st.executeQuery()){rs.next();if(rs.getLong(1)>0)throw new IllegalStateException("Ya existe una aprobación para este animal");}
                    }
                    try(PreparedStatement st=db.prepareStatement("SELECT COUNT(*) FROM evaluacion WHERE postulacion_id=? AND entrevista='FAVORABLE' AND vivienda='FAVORABLE' AND encuentro='FAVORABLE'")){
                        st.setLong(1,postId);try(ResultSet rs=st.executeQuery()){rs.next();if(rs.getLong(1)==0)throw new IllegalStateException("Entrevista, vivienda y encuentro deben ser favorables");}
                    }
                }
                long id;
                try(PreparedStatement st=db.prepareStatement("INSERT INTO decision(postulacion_id,resultado,fundamento,usuario_id) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                    st.setLong(1,postId);st.setString(2,result);st.setString(3,reason);st.setLong(4,userId);st.executeUpdate();
                    try(ResultSet rs=st.getGeneratedKeys()){rs.next();id=rs.getLong(1);}
                }
                try(PreparedStatement st=db.prepareStatement("UPDATE postulacion SET estado=?,motivo_estado=? WHERE id=?")){
                    st.setString(1,result);st.setString(2,"Decisión fundada: "+reason.substring(0,Math.min(470,reason.length())));
                    st.setLong(3,postId);st.executeUpdate();
                }
                audit(db,userId,"decision",id,"DECISION",result+": "+reason);
                db.commit();return id;
            }catch(Exception e){db.rollback();throw e;}
        }
    }

    public long adopt(long decisionId,LocalDate delivery,boolean signed,long userId) throws Exception {
        if(!signed||delivery==null)throw new IllegalArgumentException("Fecha de entrega y compromiso firmado son obligatorios");
        try(Connection db=Database.open()) {
            db.setAutoCommit(false);
            try {
                long animalId;String result;
                try(PreparedStatement st=db.prepareStatement("SELECT p.animal_id,d.resultado FROM decision d JOIN postulacion p ON p.id=d.postulacion_id WHERE d.id=? FOR UPDATE")){
                    st.setLong(1,decisionId);try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Decisión inexistente");animalId=rs.getLong(1);result=rs.getString(2);}
                }
                if(!result.equals("APROBADA"))throw new IllegalStateException("La decisión no fue aprobada");
                try(PreparedStatement st=db.prepareStatement("SELECT estado FROM animal WHERE id=? FOR UPDATE")){
                    st.setLong(1,animalId);try(ResultSet rs=st.executeQuery()){rs.next();if(!rs.getString(1).equals("APTO"))throw new IllegalStateException("Animal no apto o ya adoptado");}
                }
                long id;
                try(PreparedStatement st=db.prepareStatement("INSERT INTO adopcion(decision_id,fecha_entrega,compromiso_firmado) VALUES (?,?,TRUE)",Statement.RETURN_GENERATED_KEYS)){
                    st.setLong(1,decisionId);st.setDate(2,Date.valueOf(delivery));st.executeUpdate();
                    try(ResultSet rs=st.getGeneratedKeys()){rs.next();id=rs.getLong(1);}
                }
                schedule(db,id,"INICIAL",delivery.plusDays(2),delivery.plusDays(3));
                schedule(db,id,"DIA15",delivery.plusDays(15),delivery.plusDays(15));
                schedule(db,id,"DIA30",delivery.plusDays(30),delivery.plusDays(30));
                try(PreparedStatement st=db.prepareStatement("UPDATE animal SET estado='ADOPTADO' WHERE id=?")){st.setLong(1,animalId);st.executeUpdate();}
                audit(db,userId,"adopcion",id,"ENTREGA","Compromiso firmado; 3 seguimientos programados");
                db.commit();return id;
            }catch(Exception e){db.rollback();throw e;}
        }
    }

    public void followUp(long followId,String channel,String result,String notes,Long alertOwner,long userId) throws Exception {
        if(channel==null||channel.isBlank()||notes==null||notes.isBlank())throw new IllegalArgumentException("Canal y observaciones obligatorios");
        if(!result.equals("SATISFACTORIO")&&!result.equals("ACOMPANAMIENTO")&&!result.equals("ALERTA")&&!result.equals("POSIBLE_DEVOLUCION")&&!result.equals("SIN_RESPUESTA"))throw new IllegalArgumentException("Resultado inválido");
        try(Connection db=Database.open()) {
            db.setAutoCommit(false);
            try {
                long adoption;
                try(PreparedStatement st=db.prepareStatement("SELECT adopcion_id,fecha_contacto FROM seguimiento WHERE id=? FOR UPDATE")){
                    st.setLong(1,followId);try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Seguimiento inexistente");adoption=rs.getLong(1);if(rs.getTimestamp(2)!=null)throw new IllegalStateException("El seguimiento ya fue registrado");}
                }
                try(PreparedStatement st=db.prepareStatement("UPDATE seguimiento SET fecha_contacto=NOW(),canal=?,observaciones=?,resultado=?,responsable_id=? WHERE id=?")){
                    st.setString(1,channel);st.setString(2,notes);st.setString(3,result);st.setLong(4,userId);st.setLong(5,followId);st.executeUpdate();
                }
                if(result.equals("SIN_RESPUESTA")||result.equals("ACOMPANAMIENTO"))schedule(db,adoption,"EXTRA",LocalDate.now().plusDays(2),LocalDate.now().plusDays(3));
                if(result.equals("ALERTA")||result.equals("POSIBLE_DEVOLUCION")) {
                    if(alertOwner==null)throw new IllegalArgumentException("La alerta exige responsable");
                    try(PreparedStatement st=db.prepareStatement("INSERT INTO accion_alerta(seguimiento_id,motivo,accion,fecha_limite,responsable_id) VALUES (?,?,?,?,?)")){
                        st.setLong(1,followId);st.setString(2,result+": "+notes);st.setString(3,"Contactar y resolver el caso");st.setDate(4,Date.valueOf(LocalDate.now().plusDays(2)));st.setLong(5,alertOwner);st.executeUpdate();
                    }
                    schedule(db,adoption,"EXTRA",LocalDate.now().plusDays(2),LocalDate.now().plusDays(3));
                    if(result.equals("POSIBLE_DEVOLUCION"))try(PreparedStatement st=db.prepareStatement("UPDATE adopcion SET estado='DEVOLUCION_PENDIENTE' WHERE id=?")){st.setLong(1,adoption);st.executeUpdate();}
                }
                audit(db,userId,"seguimiento",followId,"CONTACTO",result+": "+notes);
                db.commit();
            }catch(Exception e){db.rollback();throw e;}
        }
    }

    public void closeAlert(long alertId,String action,long userId) throws Exception {
        if(action==null||action.isBlank())throw new IllegalArgumentException("Registrar la acción realizada");
        try(Connection db=Database.open()){
            db.setAutoCommit(false);
            try(PreparedStatement st=db.prepareStatement("UPDATE accion_alerta SET estado='CERRADA',accion=?,fecha_cierre=NOW(),responsable_id=? WHERE id=? AND estado='ABIERTA'")){
                st.setString(1,action);st.setLong(2,userId);st.setLong(3,alertId);
                if(st.executeUpdate()!=1)throw new IllegalArgumentException("Alerta inexistente o cerrada");
                audit(db,userId,"accion_alerta",alertId,"CIERRE",action);
                db.commit();
            }catch(Exception e){db.rollback();throw e;}
        }
    }

    private static void requireChoice(String s){if(!s.equals("FAVORABLE")&&!s.equals("REVISAR")&&!s.equals("DESFAVORABLE"))throw new IllegalArgumentException("Valoración inválida");}
    private static void setBoolean(PreparedStatement st,int index,Boolean value)throws SQLException{
        if(value==null)st.setNull(index,Types.BOOLEAN);else st.setBoolean(index,value);
    }
    private static void schedule(Connection db,long adoption,String type,LocalDate planned,LocalDate deadline)throws SQLException{
        try(PreparedStatement st=db.prepareStatement("INSERT INTO seguimiento(adopcion_id,tipo,fecha_programada,fecha_limite) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE fecha_limite=GREATEST(fecha_limite,VALUES(fecha_limite))")){
            st.setLong(1,adoption);st.setString(2,type);st.setDate(3,Date.valueOf(planned));st.setDate(4,Date.valueOf(deadline));st.executeUpdate();
        }
    }
    private static void audit(Connection db,long user,String entity,long id,String event,String detail)throws SQLException{
        try(PreparedStatement st=db.prepareStatement("INSERT INTO auditoria_evento(usuario_id,entidad,entidad_id,evento,detalle) VALUES (?,?,?,?,?)")){
            st.setLong(1,user);st.setString(2,entity);st.setLong(3,id);st.setString(4,event);st.setString(5,detail.substring(0,Math.min(500,detail.length())));st.executeUpdate();
        }
    }
}
