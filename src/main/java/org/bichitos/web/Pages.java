package org.bichitos.web;

import org.bichitos.repository.CaseQueries;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** HTML del prototipo local. Cada acción se presenta dentro del expediente seleccionado. */
final class Pages {
    private static final CaseQueries Q=App.QUERIES;
    private Pages(){}

    static String render(Map<String,String> params)throws SQLException{
        long postId=id(params,"postulacion"),animalId=id(params,"animal"),loteId=id(params,"lote");
        long decisionId=id(params,"decision"),adoptionId=id(params,"adopcion");
        long followId=id(params,"seguimiento"),alertId=id(params,"alerta");
        Map<String,Object> post=postId==0?null:Q.one(
                "SELECT p.*,t.tipo_documento,t.numero_documento,t.nombre,t.fecha_nacimiento,t.domicilio,t.localidad,t.telefono,t.correo,a.nombre AS animal,a.necesidades_cuidado,a.estado AS animal_estado FROM postulacion p JOIN postulante t ON t.id=p.postulante_id JOIN animal a ON a.id=p.animal_id WHERE p.id=?",postId);
        if(post!=null)animalId=App.number(post,"animal_id");
        if(animalId==0){
            Map<String,Object> first=Q.one("SELECT id FROM animal ORDER BY id LIMIT 1");
            if(first!=null)animalId=App.number(first,"id");
        }
        Map<String,Object> animal=animalId==0?null:Q.one(
                "SELECT id,nombre,especie,necesidades_cuidado,estado FROM animal WHERE id=?",animalId);
        StringBuilder out=new StringBuilder();
        out.append("""
                <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Bichitos | Gestión de adopciones</title><link rel="stylesheet" href="/assets/app.css"></head><body data-postulacion="__POST_ID__">
                <div class="app-shell"><aside class="sidebar"><a class="brand" href="/"><span class="brand-mark">b</span><span><strong>bichitos</strong><small>zona norte</small></span></a>
                <div class="workspace-label">ESPACIO DE TRABAJO</div><nav class="side-nav" aria-label="Navegación principal">
                <button class="nav-link active" data-view="overview"><span class="nav-icon">⌂</span>Resumen</button>
                <button class="nav-link" data-view="importar"><span class="nav-icon">⇧</span>Importar</button>
                <button class="nav-link" data-view="postulaciones"><span class="nav-icon">♡</span>Postulaciones</button>
                <button class="nav-link" data-view="evaluaciones"><span class="nav-icon">◎</span>Evaluar y comparar</button>
                <button class="nav-link" data-view="adopciones"><span class="nav-icon">⌁</span>Decisión y adopción</button>
                <button class="nav-link" data-view="seguimientos"><span class="nav-icon">◷</span>Seguimientos</button></nav>
                <div class="sidebar-bottom"><span class="avatar">BZ</span><span class="profile"><strong>Equipo Bichitos</strong><small>Cuenta local de prueba</small></span></div></aside>
                <main class="main-area"><header class="topbar"><div class="breadcrumb">Bichitos <span>/</span> <strong id="crumb-current">Resumen</strong></div>
                <div class="topbar-right"><span class="local-badge">● Entorno local</span></div></header>
                <div class="content-wrap"><div class="page-heading"><div><p class="eyebrow">GESTIÓN DE ADOPCIONES</p><h1 id="page-title">Trabajo por expedientes</h1>
                <p class="page-subtitle" id="page-subtitle">Una acción clara para cada etapa.</p></div></div>
                <div id="flash" class="notice" role="status" hidden></div>
                """);
        overview(out);
        importing(out,loteId);
        applications(out,post,loteId);
        evaluations(out,post,animal);
        adoptions(out,post,animal,decisionId);
        followups(out,adoptionId,followId,alertId);
        out.append("""
                </div><footer class="app-footer">Bichitos Zona Norte · Prototipo académico · Solo datos ficticios</footer></main></div>
                <script src="/assets/app.js"></script></body></html>
                """);
        return out.toString().replace("__POST_ID__",postId==0?"":Long.toString(postId));
    }

    private static void overview(StringBuilder out)throws SQLException{
        Map<String,Object> counts=Q.one(
                "SELECT COUNT(*) AS total,SUM(estado IN ('RECIBIDA','PENDIENTE')) AS pendientes,SUM(estado='ELEGIBLE') AS elegibles FROM postulacion");
        List<Map<String,Object>> due=Q.rows(
                "SELECT s.id,a.nombre AS animal,t.nombre AS postulante,s.fecha_limite FROM seguimiento s JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id WHERE s.fecha_contacto IS NULL AND s.fecha_limite<CURDATE() ORDER BY s.fecha_limite LIMIT 5");
        sectionOpen(out,"overview","Resumen","Un proceso continuo","Importá, revisá, evaluá y acompañá cada adopción.");
        out.append("<div class='welcome-card'><div class='welcome-copy'><span class='welcome-kicker'>PRÓXIMA ACCIÓN</span><h2>Empezá por las solicitudes recibidas.</h2><p>Los datos quedan vinculados con el animal y acompañan cada decisión hasta el seguimiento.</p><button class='button button-light' data-go='importar'>Importar CSV →</button></div></div>");
        out.append("<div class='flow-steps'>");
        for(String stage:new String[]{"Importar","Revisar solicitud","Verificar","Evaluar y comparar","Decidir","Registrar adopción","Seguimientos"})
            out.append("<span>").append(App.esc(stage)).append("</span>");
        out.append("</div>");
        out.append("<div class='metric-grid'><div class='panel-card'><strong>").append(display(counts,"total")).append("</strong><p>Solicitudes</p></div><div class='panel-card'><strong>").append(display(counts,"pendientes")).append("</strong><p>Por verificar</p></div><div class='panel-card'><strong>").append(display(counts,"elegibles")).append("</strong><p>Elegibles para evaluar</p></div></div>");
        out.append("<div class='section-head'><div><h2>Seguimientos vencidos</h2><p>El equipo debe atenderlos primero.</p></div></div>");
        if(due.isEmpty())out.append("<p class='empty-state'>No hay tareas vencidas.</p>");
        else{
            out.append("<div class='stack-list'>");
            for(var row:due)out.append("<a class='record-link' href='/?seguimiento=").append(App.number(row,"id"))
                    .append("#seguimientos'><strong>").append(display(row,"animal")).append(" · ").append(display(row,"postulante"))
                    .append("</strong><span>Venció el ").append(display(row,"fecha_limite")).append(" →</span></a>");
            out.append("</div>");
        }
        out.append("<p class='demo-note'>El prototipo usa CSV y HTTP local. En la configuración predeterminada, cada inicio de Java repone los datos ficticios y descarta cambios previos. La sincronización con Google Sheets, los roles reales y HTTPS permanecen diseñados, no implementados.</p>");
        sectionClose(out);
    }

    private static void importing(StringBuilder out,long loteId)throws SQLException{
        sectionOpen(out,"importar","Importar","Importar postulaciones","Seleccioná un CSV; el sistema revisa cada fila y evita duplicados.");
        out.append("""
                <div class="panel-card workflow-panel"><h2>1. Seleccionar archivo CSV</h2>
                <p>Elegí un archivo de hasta 2 MB. Debe tener el encabezado del ejemplo y columnas separadas por punto y coma.</p>
                <form action="/import" class="form-stack" data-upload><label>Archivo CSV
                <input type="file" name="archivo" accept=".csv,text/csv" required></label>
                <button class="button button-primary" type="submit">Importar y revisar</button></form>
                <p class="helper">Cada fila debe tener todos los datos obligatorios. Las filas incompletas o con un animal inexistente se rechazan y aparecen en el resultado del lote.</p></div>
                """);
        if(loteId>0){
            Map<String,Object> batch=Q.one("SELECT * FROM importacion_lote WHERE id=?",loteId);
            if(batch!=null){
                Map<String,Object> pending=Q.one("SELECT COUNT(*) AS total FROM postulacion WHERE importacion_lote_id=? AND estado IN ('RECIBIDA','PENDIENTE')",loteId);
                out.append("<div class='panel-card result-panel'><h2>Resultado del lote #").append(loteId).append("</h2><p>Archivo: ")
                        .append(display(batch,"archivo")).append("</p><div class='metric-grid'>");
                metric(out,"Incorporadas",display(batch,"incorporadas"));
                metric(out,"Por verificar",display(pending,"total"));
                metric(out,"Duplicadas",display(batch,"duplicadas"));
                metric(out,"Rechazadas",display(batch,"fallidas"));
                out.append("</div><a class='button button-primary' href='/?lote=").append(loteId)
                        .append("#postulaciones'>Revisar las solicitudes del lote →</a></div>");
                List<Map<String,Object>> errors=Q.rows("SELECT numero_fila,id_origen,detalle FROM importacion_error WHERE lote_id=? ORDER BY numero_fila",loteId);
                if(!errors.isEmpty()){
                    out.append("<h2>Filas con errores</h2><div class='stack-list'>");
                    for(var row:errors)out.append("<div class='record-link'><strong>Fila ").append(display(row,"numero_fila"))
                            .append(" · ").append(display(row,"id_origen")).append("</strong><span>")
                            .append(display(row,"detalle")).append("</span></div>");
                    out.append("</div>");
                }
            }
        }
        out.append("<div class='section-head'><div><h2>Últimas importaciones</h2><p>Abrí un lote para revisar sus resultados.</p></div></div><div class='stack-list'>");
        for(var row:Q.rows("SELECT id,archivo,inicio,incorporadas,duplicadas,fallidas FROM importacion_lote ORDER BY id DESC LIMIT 12")){
            long id=App.number(row,"id");
            out.append("<a class='record-link' href='/?lote=").append(id).append("#importar'><strong>Lote #")
                    .append(id).append(" · ").append(display(row,"archivo")).append("</strong><span>")
                    .append(display(row,"incorporadas")).append(" nuevas · ").append(display(row,"duplicadas"))
                    .append(" duplicadas · ").append(display(row,"fallidas")).append(" fallidas →</span></a>");
        }
        out.append("</div>");
        sectionClose(out);
    }

    private static void applications(StringBuilder out,Map<String,Object> post,long loteId)throws SQLException{
        sectionOpen(out,"postulaciones","Postulaciones","Revisar solicitudes","Abrí una solicitud para ver sus datos y la siguiente acción.");
        if(loteId>0)out.append("<p class='selection-note'>Mostrando solicitudes incorporadas en el lote #").append(loteId)
                .append(". <a href='/#postulaciones'>Ver todas</a></p>");
        List<Map<String,Object>> posts=loteId>0
                ?Q.rows("SELECT p.id,a.nombre AS animal,t.nombre AS postulante,p.estado,p.motivo_estado FROM postulacion p JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id WHERE p.importacion_lote_id=? ORDER BY p.id DESC",loteId)
                :Q.rows("SELECT p.id,a.nombre AS animal,t.nombre AS postulante,p.estado,p.motivo_estado FROM postulacion p JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id ORDER BY p.id DESC LIMIT 100");
        out.append("<div class='stack-list'>");
        for(var row:posts){
            long id=App.number(row,"id");
            out.append("<a class='record-link' href='/?postulacion=").append(id).append("#postulaciones'><strong>#")
                    .append(id).append(" · ").append(display(row,"animal")).append(" · ")
                    .append(display(row,"postulante")).append("</strong><span class='status-tag'>")
                    .append(App.esc(App.label(str(row,"estado")))).append("</span><small>")
                    .append(display(row,"motivo_estado")).append("</small><b>Revisar →</b></a>");
        }
        if(posts.isEmpty())out.append("<p class='empty-state'>No hay solicitudes en esta lista.</p>");
        out.append("</div>");
        if(post!=null)applicationDetail(out,post);
        sectionClose(out);
    }

    private static void applicationDetail(StringBuilder out,Map<String,Object> post)throws SQLException{
        long postId=App.number(post,"id");
        String state=str(post,"estado");
        out.append("<div class='section-head'><div><h2>Expediente #").append(postId).append(" · ")
                .append(display(post,"animal")).append(" / ").append(display(post,"nombre"))
                .append("</h2><p>").append(display(post,"motivo_estado")).append("</p></div><span class='status-tag'>")
                .append(App.esc(App.label(state))).append("</span></div>");
        out.append("<div class='detail-grid'><article class='panel-card'><h2>Datos declarados</h2><dl class='facts'>");
        fact(out,"Documento",str(post,"tipo_documento")+" "+str(post,"numero_documento"));
        fact(out,"Nacimiento",display(post,"fecha_nacimiento"));
        fact(out,"Domicilio",display(post,"domicilio")+" · "+display(post,"localidad"));
        fact(out,"Contacto",display(post,"telefono")+" · "+display(post,"correo"));
        fact(out,"Alquila",yesNo(post.get("alquila")));
        fact(out,"Consentimiento declarado",yesNo(post.get("consentimiento_propietario")));
        fact(out,"Tiempo y recursos",yesNo(post.get("tiempo_y_recursos")));
        out.append("</dl><p><strong>Animal:</strong> ").append(display(post,"animal"))
                .append(". <strong>Necesidades:</strong> ").append(display(post,"necesidades_cuidado")).append("</p></article>");
        out.append("<article class='panel-card'><h2>Animales que desea adoptar</h2><p>Una misma persona puede postularse para uno o varios animales.</p><div class='desired-animals'>");
        for(var desired:Q.rows("SELECT p.id,a.nombre,a.especie,p.estado FROM postulacion p JOIN animal a ON a.id=p.animal_id WHERE p.postulante_id=? ORDER BY p.id",App.number(post,"postulante_id")))
            out.append("<a class='desired-animal ").append(App.number(desired,"id")==postId?"current":"").append("' href='/?postulacion=").append(App.number(desired,"id")).append("#postulaciones'><strong>")
                    .append(display(desired,"nombre")).append("</strong><span>").append(display(desired,"especie")).append(" · ")
                    .append(App.esc(App.label(str(desired,"estado")))).append("</span></a>");
        out.append("</div></article>");
        out.append("</div>");
        Map<String,Object> verification=Q.one("SELECT identidad_confirmada,domicilio_confirmado,consentimiento_confirmado,fecha FROM verificacion WHERE postulacion_id=?",postId);
        if(state.equals("PENDIENTE")||state.equals("RECIBIDA")||state.equals("ELEGIBLE")){
            out.append("<article class='panel-card workflow-panel'><h2>2. Verificar elegibilidad</h2><p>La edad y los recursos se revisan con los datos declarados. Confirmá identidad y domicilio; el consentimiento solo corresponde si alquila.</p>");
            if(verification!=null)out.append("<p>Última verificación: ").append(display(verification,"fecha")).append("</p>");
            out.append("<form action='/verify' class='form-stack'>");hidden(out,"postulacion",postId);
            triSelect(out,"Identidad","identidad",verification==null?null:verification.get("identidad_confirmada"));
            triSelect(out,"Domicilio","domicilio",verification==null?null:verification.get("domicilio_confirmado"));
            if(Boolean.TRUE.equals(post.get("alquila")))
                triSelect(out,"Consentimiento del propietario","propietario",verification==null?null:verification.get("consentimiento_confirmado"));
            else out.append("<input type='hidden' name='propietario' value='si'><p class='helper'>Consentimiento del propietario: no aplica porque no alquila.</p>");
            out.append("<button class='button button-primary' type='submit'>Guardar verificación</button></form></article>");
        }
        if(state.equals("ELEGIBLE")||state.equals("EVALUADA")||state.equals("EN_ESPERA")||state.equals("APROBADA")){
            out.append("<p class='next-action'><strong>Próxima etapa:</strong> <a href='/?postulacion=").append(postId)
                    .append("&animal=").append(App.number(post,"animal_id"))
                    .append("#evaluaciones'>Evaluar y comparar candidaturas →</a></p>");
        }
    }

    private static void evaluations(StringBuilder out,Map<String,Object> post,Map<String,Object> animal)throws SQLException{
        sectionOpen(out,"evaluaciones","Evaluar y comparar","Evaluar y comparar candidatos",
                "La comparación informa la decisión humana; no genera puntajes ni elige adoptantes.");
        if(animal==null){out.append("<p class='empty-state'>Primero registrá un animal.</p>");sectionClose(out);return;}
        long animalId=App.number(animal,"id");
        boolean animalApto="APTO".equals(str(animal,"estado"));
        out.append("<div class='panel-card workflow-panel'><h2>").append(display(animal,"nombre"))
                .append(" · ").append(display(animal,"especie")).append("</h2><p><strong>Estado:</strong> ")
                .append(App.esc(App.label(str(animal,"estado")))).append("</p><p><strong>Necesidades de cuidado:</strong> ")
                .append(display(animal,"necesidades_cuidado")).append("</p></div>");
        out.append("<div class='toolbar-panel'><label class='animal-selector'>Animal en evaluación<select onchange=\"location.href='/?animal='+this.value+'#evaluaciones'\">");
        for(var a:Q.rows("SELECT id,nombre,especie,estado FROM animal ORDER BY (estado='APTO') DESC,nombre"))
            out.append("<option value='").append(App.number(a,"id")).append("'").append(App.number(a,"id")==animalId?" selected":"").append(">")
                    .append(display(a,"nombre")).append(" · ").append(display(a,"especie")).append(" · ")
                    .append(App.esc(App.label(str(a,"estado")))).append("</option>");
        out.append("</select></label><span class='toolbar-hint'>").append(display(animal,"estado")).append(" · ")
                .append(display(animal,"necesidades_cuidado")).append("</span></div>");
        List<Map<String,Object>> candidates=Q.rows(
                "SELECT p.id,p.estado,t.nombre,t.localidad,p.alquila,p.tiempo_y_recursos,e.entrevista,e.vivienda,e.encuentro,e.observaciones,e.fecha,u.nombre AS evaluador FROM postulacion p JOIN postulante t ON t.id=p.postulante_id LEFT JOIN evaluacion e ON e.postulacion_id=p.id LEFT JOIN usuario u ON u.id=e.usuario_id WHERE p.animal_id=? AND p.estado IN ('ELEGIBLE','EVALUADA','EN_ESPERA','APROBADA') ORDER BY p.id",animalId);
        out.append("<div class='section-head'><div><h2>Candidaturas para este animal</h2><p>Valoraciones cualitativas comparables.</p></div><input class='candidate-search' data-candidate-search placeholder='Buscar candidato…' aria-label='Buscar candidato'></div><div class='comparison-grid'>");
        for(var c:candidates){
            long id=App.number(c,"id");
            boolean selected=post!=null&&App.number(post,"id")==id;
            out.append("<article data-candidate-name='").append(App.esc(str(c,"nombre").toLowerCase())).append("' class='panel-card candidate-card").append(selected?" candidate-selected":"")
                    .append("'><h2>").append(display(c,"nombre")).append(" <span class='status-tag'>")
                    .append(App.esc(App.label(str(c,"estado")))).append("</span></h2>");
            if(selected)out.append("<p class='selected-label'>Candidatura seleccionada para ")
                    .append(animalApto?"editar":"consultar").append("</p>");
            out.append("<dl class='facts'>");
            fact(out,"Localidad",display(c,"localidad"));fact(out,"Alquila",yesNo(c.get("alquila")));
            fact(out,"Tiempo y recursos",yesNo(c.get("tiempo_y_recursos")));
            fact(out,"Entrevista",App.esc(App.label(str(c,"entrevista"))));
            fact(out,"Vivienda",App.esc(App.label(str(c,"vivienda"))));
            fact(out,"Encuentro",App.esc(App.label(str(c,"encuentro"))));
            out.append("</dl><p>").append(display(c,"observaciones")).append("</p>");
            if(c.get("fecha")!=null)out.append("<p><strong>Evaluación:</strong> ").append(display(c,"fecha"))
                    .append(" · ").append(display(c,"evaluador")).append("</p>");
            for(var criterion:Q.rows(
                    "SELECT cr.nombre,ec.valoracion,ec.observaciones FROM evaluacion_criterio ec JOIN criterio cr ON cr.id=ec.criterio_id JOIN evaluacion e ON e.id=ec.evaluacion_id WHERE e.postulacion_id=? ORDER BY cr.id",id)){
                out.append("<p class='criterion-note'><strong>").append(display(criterion,"nombre")).append(":</strong> ")
                        .append(App.esc(App.label(str(criterion,"valoracion")))).append(" · ")
                        .append(display(criterion,"observaciones")).append("</p>");
            }
            out.append("<a href='/?postulacion=").append(id).append("&animal=").append(animalId)
                    .append("#evaluaciones'>").append(animalApto
                            ? (selected?"Ver formulario de esta persona →":"Evaluar a "+display(c,"nombre")+" →")
                            : "Ver evaluación de "+display(c,"nombre")+" →").append("</a></article>");
        }
        if(candidates.isEmpty())out.append("<p class='empty-state'>Todavía no hay postulantes elegibles para este animal. Revisá las solicitudes por verificar.</p>");
        out.append("</div>");
        if(!animalApto)out.append("<p class='notice'>Este animal ya no está apto para nuevas evaluaciones o decisiones. Las valoraciones anteriores se muestran solo para consulta.</p>");
        if(post!=null&&App.number(post,"animal_id")==animalId
                &&animalApto
                &&(str(post,"estado").equals("ELEGIBLE")||str(post,"estado").equals("EVALUADA"))){
            long id=App.number(post,"id");
            Map<String,Object> evaluation=Q.one("SELECT * FROM evaluacion WHERE postulacion_id=?",id);
            out.append("<article class='panel-card workflow-panel'><h2>3. Evaluación de ").append(display(post,"nombre"))
                    .append("</h2><p class='selected-label'>Estás editando únicamente el expediente de ")
                    .append(display(post,"nombre")).append(". Para cambiar de persona, seleccioná otra candidatura arriba.</p>")
                    .append("<p>Registrá entrevista, vivienda y encuentro. Cada criterio aplicable necesita valoración y observación.</p>");
            out.append("<form action='/evaluate' class='form-stack'>");hidden(out,"postulacion",id);
            choice(out,"Entrevista","entrevista",evaluation);
            activityDate(out,"ENTREVISTA",evaluation);
            choice(out,"Vivienda","vivienda",evaluation);
            activityDate(out,"VIVIENDA",evaluation);
            choice(out,"Encuentro","encuentro",evaluation);
            activityDate(out,"ENCUENTRO",evaluation);
            out.append("<label>Observaciones generales<textarea name='observaciones' required rows='3'>")
                    .append(evaluation==null?"":displayRaw(evaluation,"observaciones")).append("</textarea></label>");
            List<Map<String,Object>> criteria=Q.rows(
                    "SELECT c.id,c.nombre,c.descripcion,ec.valoracion,ec.observaciones FROM animal_criterio ac JOIN criterio c ON c.id=ac.criterio_id LEFT JOIN evaluacion e ON e.postulacion_id=? LEFT JOIN evaluacion_criterio ec ON ec.evaluacion_id=e.id AND ec.criterio_id=c.id WHERE ac.animal_id=? AND c.activo=TRUE ORDER BY c.id",id,animalId);
            for(var c:criteria){
                long criterion=App.number(c,"id");
                out.append("<fieldset class='criterion-field'><legend>").append(display(c,"nombre"))
                        .append("</legend><p>").append(display(c,"descripcion")).append("</p><label>Valoración<select name='c_")
                        .append(criterion).append("_valor' required>");
                option(out,"","Seleccionar",str(c,"valoracion"));
                for(String value:new String[]{"FAVORABLE","REVISAR","DESFAVORABLE","NO_APLICA"})
                    option(out,value,App.label(value),str(c,"valoracion"));
                out.append("</select></label><label>Observación<textarea name='c_").append(criterion)
                        .append("_nota' required rows='2'>").append(displayRaw(c,"observaciones"))
                        .append("</textarea></label></fieldset>");
            }
            if(criteria.isEmpty())out.append("<p class='notice notice-error'>No hay criterios configurados para este animal; no se puede evaluar.</p>");
            else out.append("<button class='button button-primary' type='submit'>Guardar evaluación</button>");
            out.append("</form></article>");
        }
        if(animalApto&&post!=null&&(str(post,"estado").equals("EVALUADA")||str(post,"estado").equals("EN_ESPERA")))
            out.append("<p class='next-action'><strong>Próxima etapa:</strong> <a href='/?postulacion=")
                    .append(App.number(post,"id")).append("#adopciones'>Registrar decisión fundada →</a></p>");
        sectionClose(out);
    }

    private static void adoptions(StringBuilder out,Map<String,Object> post,Map<String,Object> animal,long decisionId)throws SQLException{
        sectionOpen(out,"adopciones","Decisión y adopción","Decidir y registrar la entrega",
                "El equipo decide con fundamento; la adopción exige compromiso firmado.");
        if(animal!=null)out.append("<p class='selection-note'><strong>Animal:</strong> ").append(display(animal,"nombre"))
                .append(" · ").append(display(animal,"necesidades_cuidado")).append("</p>");
        if(animal!=null){
            out.append("<div class='toolbar-panel'><label class='animal-selector'>Animal en decisión<select onchange=\"location.href='/?animal='+this.value+'#adopciones'\">");
            for(var a:Q.rows("SELECT id,nombre,especie,estado FROM animal ORDER BY nombre"))
                out.append("<option value='").append(App.number(a,"id")).append("'").append(App.number(a,"id")==App.number(animal,"id")?" selected":"").append(">")
                        .append(display(a,"nombre")).append(" · ").append(display(a,"especie")).append("</option>");
            out.append("</select></label></div>");
        }
        if(animal!=null){
            out.append("<div class='candidate-switcher'><label>Candidato a decidir<select onchange=\"location.href='/?animal=").append(App.number(animal,"id")).append("&postulacion='+this.value+'#adopciones'\">");
            for(var c:Q.rows("SELECT p.id,t.nombre,p.estado FROM postulacion p JOIN postulante t ON t.id=p.postulante_id WHERE p.animal_id=? AND p.estado IN ('EVALUADA','EN_ESPERA','APROBADA') ORDER BY t.nombre",App.number(animal,"id")))
                out.append("<option value='").append(App.number(c,"id")).append("'").append(post!=null&&App.number(post,"id")==App.number(c,"id")?" selected":"").append(">").append(display(c,"nombre")).append(" · ").append(App.esc(App.label(str(c,"estado")))).append("</option>");
            out.append("</select></label><span class='toolbar-hint'>El cambio conserva el animal y abre la decisión del candidato elegido.</span></div>");
        }
        if(post==null)out.append("<p class='empty-state'>Seleccioná un candidato evaluado para decidir.</p>");
        else{
            long postId=App.number(post,"id");
            out.append("<div class='panel-card workflow-panel'><h2>Solicitud #").append(postId).append(" · ")
                    .append(display(post,"nombre")).append("</h2><p>Estado: ")
                    .append(App.esc(App.label(str(post,"estado")))).append("</p></div>");
            if(animal!=null&&"APTO".equals(str(animal,"estado"))
                    &&(str(post,"estado").equals("EVALUADA")||str(post,"estado").equals("EN_ESPERA"))){
                out.append("<article class='panel-card workflow-panel'><h2>4. Decisión del equipo</h2><form action='/decide' class='form-stack'>");
                hidden(out,"postulacion",postId);
                out.append("<label>Resultado<select name='resultado' required>");
                for(String value:new String[]{"APROBADA","EN_ESPERA","RECHAZADA"})
                    option(out,value,App.label(value),null);
                out.append("</select></label><label>Fundamento<textarea name='fundamento' rows='3' required placeholder='Explicá la elección según las necesidades del animal y la evaluación'></textarea></label>");
                out.append("<button class='button button-primary' type='submit'>Guardar decisión</button></form></article>");
            }
            Map<String,Object> decision=decisionId>0?Q.one("SELECT d.*,a.id AS adopcion_id FROM decision d LEFT JOIN adopcion a ON a.decision_id=d.id WHERE d.id=? AND d.postulacion_id=?",decisionId,postId):null;
            if(decision==null)decision=Q.one("SELECT d.*,a.id AS adopcion_id FROM decision d LEFT JOIN adopcion a ON a.decision_id=d.id WHERE d.postulacion_id=? ORDER BY d.id DESC LIMIT 1",postId);
            if(decision!=null){
                out.append("<article class='panel-card workflow-panel'><h2>Última decisión: ")
                        .append(App.esc(App.label(str(decision,"resultado")))).append("</h2><p><strong>Fundamento:</strong> ")
                        .append(display(decision,"fundamento")).append("</p><p><strong>Fecha:</strong> ")
                        .append(display(decision,"fecha")).append("</p>");
                if(str(decision,"resultado").equals("APROBADA")&&decision.get("adopcion_id")==null){
                    out.append("<h2>5. Registrar entrega</h2><form action='/adopt' class='form-stack'>");
                    hidden(out,"decision",App.number(decision,"id"));
                    out.append("<label>Fecha de entrega<input type='date' name='fecha' required></label>")
                            .append("<label class='check-row'><input type='checkbox' name='firmado' required> Compromiso firmado</label>")
                            .append("<button class='button button-primary' type='submit'>Confirmar adopción y programar seguimientos</button></form>");
                }else if(decision.get("adopcion_id")!=null)
                    out.append("<a href='/?adopcion=").append(App.number(decision,"adopcion_id"))
                            .append("#seguimientos'>Ver plan de seguimiento →</a>");
                out.append("</article>");
            }
        }
        out.append("<div class='section-head'><div><h2>Decisiones recientes</h2></div></div><div class='stack-list'>");
        for(var row:Q.rows("SELECT d.id,d.postulacion_id,d.resultado,t.nombre,a.nombre AS animal FROM decision d JOIN postulacion p ON p.id=d.postulacion_id JOIN postulante t ON t.id=p.postulante_id JOIN animal a ON a.id=p.animal_id ORDER BY d.id DESC LIMIT 20"))
            out.append("<a class='record-link' href='/?postulacion=").append(App.number(row,"postulacion_id"))
                    .append("&decision=").append(App.number(row,"id")).append("#adopciones'><strong>")
                    .append(display(row,"animal")).append(" · ").append(display(row,"nombre")).append("</strong><span>")
                    .append(App.esc(App.label(str(row,"resultado")))).append(" →</span></a>");
        out.append("</div>");
        sectionClose(out);
    }

    private static void followups(StringBuilder out,long adoptionId,long followId,long alertId)throws SQLException{
        sectionOpen(out,"seguimientos","Seguimientos","Acompañar después de la entrega",
                "Cada tarea muestra a quién contactar, cuándo vence y qué se registró.");
        List<Map<String,Object>> tasks=Q.rows(
                "SELECT s.id,s.adopcion_id,s.tipo,s.fecha_programada,s.fecha_limite,s.fecha_contacto,s.resultado,a.nombre AS animal,t.nombre AS postulante FROM seguimiento s JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id ORDER BY (s.fecha_contacto IS NOT NULL),s.fecha_limite,s.id LIMIT 100");
        if(followId==0&&adoptionId>0){
            for(var row:tasks)if(App.number(row,"adopcion_id")==adoptionId&&row.get("fecha_contacto")==null){
                followId=App.number(row,"id");break;
            }
        }
        out.append("<div class='section-head'><div><h2>Plan de seguimiento</h2><p>Pendientes y vencidos primero.</p></div></div><div class='stack-list'>");
        for(var row:tasks){
            long id=App.number(row,"id");
            boolean done=row.get("fecha_contacto")!=null;
            boolean overdue=!done&&java.time.LocalDate.parse(row.get("fecha_limite").toString()).isBefore(java.time.LocalDate.now());
            out.append("<a class='record-link' href='/?seguimiento=").append(id).append("#seguimientos'><strong>")
                    .append(display(row,"animal")).append(" · ").append(display(row,"postulante"))
                    .append(" · ").append(App.esc(App.label(str(row,"tipo")))).append("</strong><span class='status-tag ")
                    .append(overdue?"late":"").append("'>").append(done?"Realizado":overdue?"Vencido":"Pendiente")
                    .append("</span><small>Programado: ").append(display(row,"fecha_programada"))
                    .append(" · Límite: ").append(display(row,"fecha_limite")).append("</small><b>Ver tarea →</b></a>");
        }
        if(tasks.isEmpty())out.append("<p class='empty-state'>Todavía no hay adopciones con seguimientos programados.</p>");
        out.append("</div>");
        if(followId>0){
            Map<String,Object> selected=Q.one(
                    "SELECT s.*,ad.estado AS adopcion_estado,p.animal_id,a.nombre AS animal,a.necesidades_cuidado,t.nombre AS postulante,t.telefono,t.correo FROM seguimiento s JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id WHERE s.id=?",followId);
            if(selected!=null){
                out.append("<article class='panel-card workflow-panel'><h2>6. Contacto para ")
                        .append(display(selected,"animal")).append(" · ").append(display(selected,"postulante"))
                        .append("</h2><p>Teléfono: ").append(display(selected,"telefono")).append(" · Correo: ")
                        .append(display(selected,"correo")).append("</p><p>Fecha prevista: ")
                        .append(display(selected,"fecha_programada")).append(" · Límite: ")
                        .append(display(selected,"fecha_limite")).append("</p><p>Necesidades del animal: ")
                        .append(display(selected,"necesidades_cuidado")).append("</p>");
                if(selected.get("fecha_contacto")==null){
                    out.append("<form action='/followup' class='form-stack'>");hidden(out,"seguimiento",followId);
                    out.append("<label>Canal de contacto<input name='canal' required placeholder='Teléfono, visita o mensaje'></label>")
                            .append("<label>Resultado<select name='resultado' required>");
                    for(String value:new String[]{"SATISFACTORIO","ACOMPANAMIENTO","ALERTA","POSIBLE_DEVOLUCION","SIN_RESPUESTA"})
                        option(out,value,App.label(value),null);
                    out.append("</select></label><label>Adaptación, salud, convivencia y próxima acción<textarea name='observaciones' required rows='4'></textarea></label>")
                            .append("<button class='button button-primary' type='submit'>Guardar contacto</button></form>");
                }else out.append("<p><strong>Registrado:</strong> ").append(display(selected,"fecha_contacto"))
                        .append(" · ").append(App.esc(App.label(str(selected,"resultado"))))
                        .append("</p><p>").append(display(selected,"observaciones")).append("</p>");
                out.append("</article>");
                history(out,App.number(selected,"animal_id"));
            }
        }
        List<Map<String,Object>> alerts=Q.rows(
                "SELECT aa.id,aa.estado,aa.motivo,aa.accion,aa.fecha_limite,aa.fecha_apertura,aa.fecha_cierre,u.nombre AS responsable,a.nombre AS animal,t.nombre AS postulante FROM accion_alerta aa JOIN seguimiento s ON s.id=aa.seguimiento_id JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id JOIN usuario u ON u.id=aa.responsable_id ORDER BY (aa.estado='CERRADA'),aa.fecha_limite,aa.id LIMIT 100");
        out.append("<div class='section-head'><div><h2>Alertas y posibles devoluciones</h2><p>Las acciones abiertas permanecen visibles hasta su cierre.</p></div></div><div class='stack-list'>");
        for(var row:alerts)out.append("<a class='record-link' href='/?alerta=").append(App.number(row,"id"))
                .append("#seguimientos'><strong>").append(display(row,"animal")).append(" · ")
                .append(display(row,"postulante")).append("</strong><span class='status-tag'>")
                .append(App.esc(App.label(str(row,"estado")))).append("</span><small>")
                .append(display(row,"motivo")).append(" · Responsable: ").append(display(row,"responsable"))
                .append(" · Vence: ").append(display(row,"fecha_limite")).append("</small><b>Ver acción →</b></a>");
        if(alerts.isEmpty())out.append("<p class='empty-state'>No hay alertas registradas.</p>");
        out.append("</div>");
        if(alertId>0){
            Map<String,Object> alert=Q.one("SELECT aa.*,a.nombre AS animal,t.nombre AS postulante FROM accion_alerta aa JOIN seguimiento s ON s.id=aa.seguimiento_id JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id JOIN animal a ON a.id=p.animal_id JOIN postulante t ON t.id=p.postulante_id WHERE aa.id=?",alertId);
            if(alert!=null){
                out.append("<article class='panel-card workflow-panel'><h2>Alerta #").append(alertId)
                        .append(" · ").append(display(alert,"animal")).append(" / ").append(display(alert,"postulante"))
                        .append("</h2><p>").append(display(alert,"motivo")).append("</p><p>Acción prevista: ")
                        .append(display(alert,"accion")).append("</p><p>Fecha límite: ")
                        .append(display(alert,"fecha_limite")).append("</p>");
                if(str(alert,"estado").equals("ABIERTA"))
                    out.append("<form action='/alert' class='form-stack'><input type='hidden' name='alerta' value='")
                            .append(alertId).append("'><label>Acción realizada<textarea name='accion' required rows='3'></textarea></label>")
                            .append("<button class='button button-primary' type='submit'>Cerrar alerta</button></form>");
                else out.append("<p><strong>Cerrada:</strong> ").append(display(alert,"fecha_cierre")).append("</p>");
                out.append("</article>");
            }
        }
        sectionClose(out);
    }

    private static void history(StringBuilder out,long animalId)throws SQLException{
        String sql="""
                SELECT p.fecha AS fecha,'Postulación' AS etapa,CONCAT(COALESCE(t.nombre,'Sin nombre'),' · ',p.estado) AS detalle
                  FROM postulacion p JOIN postulante t ON t.id=p.postulante_id WHERE p.animal_id=?
                UNION ALL SELECT d.fecha,'Decisión',CONCAT(d.resultado,' · ',LEFT(d.fundamento,120))
                  FROM decision d JOIN postulacion p ON p.id=d.postulacion_id WHERE p.animal_id=?
                UNION ALL SELECT CAST(ad.fecha_entrega AS DATETIME),'Adopción','Entrega con compromiso firmado'
                  FROM adopcion ad JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id WHERE p.animal_id=?
                UNION ALL SELECT s.fecha_contacto,'Seguimiento',CONCAT(s.resultado,' · ',LEFT(s.observaciones,120))
                  FROM seguimiento s JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id WHERE p.animal_id=? AND s.fecha_contacto IS NOT NULL
                UNION ALL SELECT aa.fecha_apertura,'Alerta',LEFT(aa.motivo,120)
                  FROM accion_alerta aa JOIN seguimiento s ON s.id=aa.seguimiento_id JOIN adopcion ad ON ad.id=s.adopcion_id JOIN decision d ON d.id=ad.decision_id JOIN postulacion p ON p.id=d.postulacion_id WHERE p.animal_id=?
                ORDER BY fecha DESC
                """;
        out.append("<div class='section-head'><div><h2>Historial del animal</h2><p>Actuaciones en orden cronológico.</p></div></div><div class='stack-list'>");
        for(var row:Q.rows(sql,animalId,animalId,animalId,animalId,animalId))
            out.append("<div class='record-link'><strong>").append(display(row,"etapa")).append("</strong><small>")
                    .append(display(row,"fecha")).append("</small><span>").append(display(row,"detalle")).append("</span></div>");
        out.append("</div>");
    }

    private static void sectionOpen(StringBuilder out,String id,String title,String heading,String subtitle){
        out.append("<section class='view").append(id.equals("overview")?" active":"").append("' id='view-")
                .append(id).append("' data-title='").append(App.esc(title)).append("' data-heading='")
                .append(App.esc(heading)).append("' data-subtitle='").append(App.esc(subtitle)).append("'>");
    }
    private static void sectionClose(StringBuilder out){out.append("</section>");}
    private static void hidden(StringBuilder out,String name,long value){
        out.append("<input type='hidden' name='").append(name).append("' value='").append(value).append("'>");
    }
    private static void triSelect(StringBuilder out,String title,String name,Object current){
        String value=current==null?"":Boolean.TRUE.equals(current)?"si":"no";
        out.append("<label>").append(App.esc(title)).append("<select name='").append(name).append("'>");
        option(out,"","Pendiente de comprobar / no declarado",value);
        option(out,"si","Sí, confirmado",value);option(out,"no","No confirmado",value);
        out.append("</select></label>");
    }
    private static void choice(StringBuilder out,String title,String name,Map<String,Object> row){
        String value=row==null?"":str(row,name);
        out.append("<label>").append(App.esc(title)).append("<select name='").append(name).append("' required>");
        option(out,"","Seleccionar",value);
        for(String c:new String[]{"FAVORABLE","REVISAR","DESFAVORABLE"})option(out,c,App.label(c),value);
        out.append("</select></label>");
    }
    private static void activityDate(StringBuilder out,String type,Map<String,Object> evaluation)throws SQLException{
        Map<String,Object> activity=evaluation==null?null:Q.one(
                "SELECT ea.fecha,u.nombre AS responsable FROM evaluacion_actividad ea JOIN usuario u ON u.id=ea.responsable_id WHERE ea.evaluacion_id=? AND ea.tipo='"+type+"'",
                App.number(evaluation,"id"));
        String key="fecha_"+type.toLowerCase();
        String date=activity==null?java.time.LocalDate.now().toString():str(activity,"fecha");
        out.append("<label>Fecha de ").append(App.esc(type.toLowerCase()))
                .append("<input type='date' name='").append(key).append("' value='")
                .append(App.esc(date)).append("' required></label>");
        if(activity!=null)out.append("<p class='helper'>Registrada por ").append(display(activity,"responsable"))
                .append(" el ").append(display(activity,"fecha")).append(".</p>");
        else out.append("<p class='helper'>Responsable: cuenta local de prueba. En la solución final se toma de la sesión autenticada.</p>");
    }
    private static void option(StringBuilder out,String value,String text,String selected){
        out.append("<option value='").append(App.esc(value)).append("'");
        if(value.equals(selected))out.append(" selected");
        out.append(">").append(App.esc(text)).append("</option>");
    }
    private static void fact(StringBuilder out,String title,String value){
        out.append("<dt>").append(App.esc(title)).append("</dt><dd>").append(value).append("</dd>");
    }
    private static void metric(StringBuilder out,String title,String value){
        out.append("<div class='metric'><strong>").append(value).append("</strong><span>")
                .append(App.esc(title)).append("</span></div>");
    }
    private static String yesNo(Object value){
        return value==null?"No declarado":Boolean.TRUE.equals(value)?"Sí":"No";
    }
    private static String str(Map<String,Object> row,String key){
        Object value=row==null?null:row.get(key);return value==null?"":value.toString();
    }
    private static String display(Map<String,Object> row,String key){
        Object value=row==null?null:row.get(key);return App.esc(value);
    }
    private static String displayRaw(Map<String,Object> row,String key){
        Object value=row==null?null:row.get(key);return value==null?"":App.esc(value);
    }
    private static long id(Map<String,String> params,String key){
        try{return Long.parseLong(params.getOrDefault(key,"0"));}catch(NumberFormatException e){return 0;}
    }
}
