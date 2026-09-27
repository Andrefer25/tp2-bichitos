package org.bichitos.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas de lectura del expediente. Los parámetros siempre se vinculan por JDBC. */
public final class CaseQueries {
    public List<Map<String,Object>> rows(String sql,long...ids)throws SQLException{
        try(Connection db=Database.open();PreparedStatement st=db.prepareStatement(sql)){
            for(int i=0;i<ids.length;i++)st.setLong(i+1,ids[i]);
            try(ResultSet rs=st.executeQuery()){
                ResultSetMetaData meta=rs.getMetaData();
                List<Map<String,Object>> result=new ArrayList<>();
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));
                    result.add(row);
                }
                return result;
            }
        }
    }
    public Map<String,Object> one(String sql,long...ids)throws SQLException{
        List<Map<String,Object>> result=rows(sql,ids);
        return result.isEmpty()?null:result.get(0);
    }
}
