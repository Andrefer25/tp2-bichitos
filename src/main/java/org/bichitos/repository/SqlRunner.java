package org.bichitos.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/** Ejecutor local de los scripts SQL del proyecto, sin pasar credenciales por consola. */
public final class SqlRunner {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Uso: SqlRunner sql/02_seed.sql");
        Path script=Path.of(args[0]).normalize();
        if(script.getFileName().toString().equals("07_rehidratar_demo.sql"))
            throw new IllegalArgumentException("El reinicio de prueba solo se ejecuta mediante DemoDatabase, con verificación del esquema local.");
        try(Connection db=Database.open()) { execute(db,script); }
        System.out.println("Script ejecutado: "+script);
    }

    public static void execute(Connection db,Path script)throws Exception{
        if(!script.startsWith("sql")||!script.toString().endsWith(".sql")||!Files.isRegularFile(script))
            throw new IllegalArgumentException("Elegí un script .sql de la carpeta sql/");
        StringBuilder commands=new StringBuilder();
        for(String line:Files.readAllLines(script)){
            if(!line.trim().startsWith("--"))commands.append(line).append('\n');
        }
        try(Statement st=db.createStatement()){
            String sql=commands.toString();StringBuilder current=new StringBuilder();boolean quoted=false;
            for(int i=0;i<sql.length();i++){
                char c=sql.charAt(i);
                if(c=='\''){
                    if(quoted&&i+1<sql.length()&&sql.charAt(i+1)=='\''){
                        current.append("''");i++;continue;
                    }
                    quoted=!quoted;
                }
                if(c==';'&&!quoted){
                    if(!current.toString().isBlank())run(st,current.toString().trim());
                    current.setLength(0);
                }else current.append(c);
            }
            if(quoted)throw new IllegalArgumentException("Comillas SQL sin cerrar");
            if(!current.toString().isBlank())run(st,current.toString().trim());
        }
    }

    private static void run(Statement st,String command)throws Exception{
        if(!st.execute(command))return;
        try(ResultSet rs=st.getResultSet()){
            ResultSetMetaData meta=rs.getMetaData();
            int count=meta.getColumnCount();
            StringBuilder header=new StringBuilder();
            for(int i=1;i<=count;i++)header.append(i==1?"":" | ").append(meta.getColumnLabel(i));
            System.out.println(header);
            int rows=0;
            while(rs.next()){
                StringBuilder line=new StringBuilder();
                for(int i=1;i<=count;i++)line.append(i==1?"":" | ").append(rs.getString(i));
                System.out.println(line);
                if(++rows>=100){System.out.println("... máximo de 100 filas por consulta");break;}
            }
            if(rows==0)System.out.println("(sin filas)");
            System.out.println();
        }
    }
}
