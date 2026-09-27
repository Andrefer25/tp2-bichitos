package org.bichitos.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bichitos.application.CsvImporter;
import org.bichitos.application.WorkflowService;
import org.bichitos.repository.CaseQueries;
import org.bichitos.repository.Database;
import org.bichitos.repository.DemoDatabase;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class App {
    static final WorkflowService WORKFLOW=new WorkflowService();
    static final CaseQueries QUERIES=new CaseQueries();

    public static void main(String[] args)throws Exception{
        int port=Integer.parseInt(System.getenv().getOrDefault("BICHITOS_PORT","8080"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        try { DemoDatabase.initialize(); }
        catch (Exception error) { server.stop(0); throw error; }
        try(Connection db=Database.open();Statement st=db.createStatement();
            ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM animal")){
            rs.next();System.out.println("MySQL conectado. Animales: "+rs.getLong(1));
        }
        server.createContext("/assets/",App::asset);
        server.createContext("/",App::handle);
        server.start();
        System.out.println("Abrir http://127.0.0.1:"+port);
    }

    private static void asset(HttpExchange exchange)throws IOException{
        String name=exchange.getRequestURI().getPath().substring("/assets/".length());
        if(!name.equals("app.css")&&!name.equals("app.js")){reply(exchange,404,"text/plain","No encontrado");return;}
        Path path=Path.of("src","main","resources","web",name);
        if(!Files.isRegularFile(path)){reply(exchange,404,"text/plain","Recurso no encontrado");return;}
        byte[] bytes=Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type",name.endsWith(".css")?"text/css; charset=utf-8":"text/javascript; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(200,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}
    }

    private static void handle(HttpExchange exchange)throws IOException{
        boolean post=exchange.getRequestMethod().equals("POST");
        try{
            if(exchange.getRequestMethod().equals("GET")&&exchange.getRequestURI().getPath().equals("/")){
                reply(exchange,200,"text/html",Pages.render(params(exchange.getRequestURI().getRawQuery())));
            }else if(post){
                Action action=perform(exchange);
                reply(exchange,200,"application/json",json(true,action.message(),action.next()));
            }else reply(exchange,404,"text/plain","No encontrado");
        }catch(Exception error){
            String detail=error.getMessage()==null?"No se pudo completar la acción":error.getMessage();
            if(post)reply(exchange,400,"application/json",json(false,detail,""));
            else reply(exchange,500,"text/html","<!doctype html><meta charset='utf-8'><p>Error al consultar el expediente: "+esc(detail)+"</p>");
        }
    }

    private record Action(String message,String next){}
    private static Action perform(HttpExchange exchange)throws Exception{
        String path=exchange.getRequestURI().getPath();
        long user=1L; // Cuenta ficticia del prototipo local; no equivale a autenticación.
        if(path.equals("/import")){
            String filename=URLDecoder.decode(exchange.getRequestHeaders().getFirst("X-Filename")==null?"":exchange.getRequestHeaders().getFirst("X-Filename"),StandardCharsets.UTF_8);
            filename=Path.of(filename).getFileName().toString();
            if(filename.length()>255||!filename.toLowerCase().endsWith(".csv"))throw new IllegalArgumentException("Seleccioná un archivo .csv");
            byte[] bytes=exchange.getRequestBody().readNBytes(2_000_001);
            if(bytes.length>2_000_000)throw new IllegalArgumentException("El CSV no puede superar 2 MB");
            CsvImporter.Summary result=new CsvImporter().importReader(
                    new StringReader(new String(bytes,StandardCharsets.UTF_8)),filename,user);
            return new Action("Lote "+result.batchId()+": "+result.incorporated()+" incorporadas, "+result.duplicates()+" duplicadas y "+result.failed()+" rechazadas.",
                    "/?lote="+result.batchId()+"#importar");
        }
        byte[] body=exchange.getRequestBody().readNBytes(100_001);
        if(body.length>100_000)throw new IllegalArgumentException("Formulario demasiado grande");
        Map<String,String> f=params(new String(body,StandardCharsets.UTF_8));
        return switch(path){
            case "/verify" -> {
                long id=id(f,"postulacion");
                var result=WORKFLOW.verify(id,tri(value(f,"identidad")),tri(value(f,"domicilio")),
                        tri(value(f,"propietario")),user);
                yield new Action("Elegibilidad: "+label(result.state())+". "+result.reason(),
                        "/?postulacion="+id+"#postulaciones");
            }
            case "/evaluate" -> {
                long id=id(f,"postulacion");
                Map<String,Object> post=QUERIES.one("SELECT animal_id FROM postulacion WHERE id=?",id);
                if(post==null)throw new IllegalArgumentException("Postulación inexistente");
                long animal=number(post,"animal_id");
                Map<Long,WorkflowService.CriterionValue> values=new LinkedHashMap<>();
                for(Map<String,Object> c:QUERIES.rows(
                        "SELECT c.id FROM criterio c JOIN animal_criterio ac ON ac.criterio_id=c.id WHERE ac.animal_id=? AND c.activo=TRUE ORDER BY c.id",animal)){
                    long criterion=number(c,"id");
                    values.put(criterion,new WorkflowService.CriterionValue(
                            required(f,"c_"+criterion+"_valor"),required(f,"c_"+criterion+"_nota")));
                }
                WORKFLOW.evaluate(id,required(f,"entrevista"),required(f,"vivienda"),
                        required(f,"encuentro"),required(f,"observaciones"),values,
                        Map.of("ENTREVISTA",LocalDate.parse(required(f,"fecha_entrevista")),
                                "VIVIENDA",LocalDate.parse(required(f,"fecha_vivienda")),
                                "ENCUENTRO",LocalDate.parse(required(f,"fecha_encuentro"))),user);
                yield new Action("Evaluación registrada. Compará las candidaturas antes de decidir.",
                        "/?postulacion="+id+"&animal="+animal+"#evaluaciones");
            }
            case "/decide" -> {
                long id=id(f,"postulacion");
                long decision=WORKFLOW.decide(id,required(f,"resultado"),required(f,"fundamento"),user);
                yield new Action("Decisión fundada registrada.","/?postulacion="+id+"&decision="+decision+"#adopciones");
            }
            case "/adopt" -> {
                long decision=id(f,"decision");
                long adoption=WORKFLOW.adopt(decision,LocalDate.parse(required(f,"fecha")),
                        f.containsKey("firmado"),user);
                yield new Action("Adopción registrada. Se programaron tres seguimientos.",
                        "/?adopcion="+adoption+"#seguimientos");
            }
            case "/followup" -> {
                long follow=id(f,"seguimiento");
                String outcome=required(f,"resultado");
                Long owner=(outcome.equals("ALERTA")||outcome.equals("POSIBLE_DEVOLUCION"))
                        ?coordinator():null;
                WORKFLOW.followUp(follow,required(f,"canal"),outcome,required(f,"observaciones"),
                        owner,user);
                yield new Action("Contacto registrado; revisá las tareas y alertas del caso.",
                        "/?seguimiento="+follow+"#seguimientos");
            }
            case "/alert" -> {
                long alert=id(f,"alerta");
                WORKFLOW.closeAlert(alert,required(f,"accion"),user);
                yield new Action("Acción de alerta cerrada.","/?alerta="+alert+"#seguimientos");
            }
            default -> throw new IllegalArgumentException("Acción desconocida");
        };
    }

    private static long coordinator()throws SQLException{
        Map<String,Object> row=QUERIES.one("SELECT id FROM usuario WHERE rol='COORDINACION' AND activo=TRUE ORDER BY id LIMIT 1");
        if(row==null)throw new IllegalStateException("No hay responsable de coordinación activo para la alerta");
        return number(row,"id");
    }
    static Map<String,String> params(String raw){
        Map<String,String> result=new LinkedHashMap<>();
        if(raw==null||raw.isBlank())return result;
        for(String pair:raw.split("&")){
            String[] halves=pair.split("=",2);
            result.put(URLDecoder.decode(halves[0],StandardCharsets.UTF_8),
                    URLDecoder.decode(halves.length>1?halves[1]:"",StandardCharsets.UTF_8));
        }
        return result;
    }
    static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    static String value(Map<String,String> map,String key){return map.getOrDefault(key,"").trim();}
    static String required(Map<String,String> map,String key){
        String value=value(map,key);if(value.isBlank())throw new IllegalArgumentException("Falta "+key);return value;
    }
    static long id(Map<String,String> map,String key){
        long value=Long.parseLong(required(map,key));if(value<1)throw new IllegalArgumentException("ID inválido");return value;
    }
    static Boolean tri(String value){
        return switch(value){case "si" -> true;case "no" -> false;case "" -> null;
            default -> throw new IllegalArgumentException("Valor de verificación inválido");};
    }
    static String label(String value){
        if(value==null)return "No registrado";
        return switch(value){
            case "RECIBIDA" -> "Recibida";case "PENDIENTE" -> "Pendiente";
            case "NO_ELEGIBLE" -> "No elegible";case "ELEGIBLE" -> "Elegible";
            case "EVALUADA" -> "Evaluada";case "APROBADA" -> "Aprobada";
            case "RECHAZADA" -> "Rechazada";case "EN_ESPERA" -> "En espera";
            case "ABIERTA" -> "Abierta";case "CERRADA" -> "Cerrada";
            case "FAVORABLE" -> "Favorable";case "REVISAR" -> "Revisar";
            case "DESFAVORABLE" -> "Desfavorable";default -> value.replace('_',' ');
        };
    }
    static String esc(Object object){
        if(object==null)return "No declarado";
        return object.toString().replace("&","&amp;").replace("<","&lt;")
                .replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    private static String json(boolean ok,String message,String next){
        return "{\"ok\":"+ok+",\"message\":\""+escapeJson(message)+"\",\"next\":\""+escapeJson(next)+"\"}";
    }
    private static String escapeJson(String value){
        return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
    }
    private static void reply(HttpExchange exchange,int status,String contentType,String content)throws IOException{
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",contentType+"; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}
    }
}
