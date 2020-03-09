package app;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONValue;

// credit: https://docs.microsoft.com/en-us/sql/connect/jdbc/step-3-proof-of-concept-connecting-to-sql-using-java?view=sql-server-ver15
public class Database {

    private static Connection connection = null;

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        if (connection!=null && !connection.isClosed()) {
            return connection;
        }

        String url = "jdbc:mysql://digidata.czuuwxiu54rk.us-east-1.rds.amazonaws.com:3306";
        try  {
            connection = DriverManager.getConnection(url, "admin", "uhsDF98!");
        }
        // Handle any errors that may have occurred.
        catch (SQLException e) {
            System.out.println("Database failed to open");
            e.printStackTrace();
        }

        return connection;
    }

    public static ResultSet query(String query) throws SQLException, ClassNotFoundException {
        return Database.getConnection().createStatement().executeQuery(query);
    }

    public static int query(String query,String update) throws SQLException, ClassNotFoundException {
        return Database.getConnection().createStatement().executeUpdate(query);
    }

    // https://stackoverflow.com/questions/17160351/create-json-object-by-java-from-data-of-mysql
    public static String getJSONFromResultSet(ResultSet rs,String keyName) {
        Map json = new HashMap();
        List list = new ArrayList();
        if(rs!=null)
        {
            try {
                ResultSetMetaData metaData = rs.getMetaData();
                while(rs.next())
                {
                    Map<String,Object> columnMap = new HashMap<String, Object>();
                    for(int columnIndex=1;columnIndex<=metaData.getColumnCount();columnIndex++)
                    {
                        if(rs.getString(metaData.getColumnName(columnIndex))!=null)
                            columnMap.put(metaData.getColumnLabel(columnIndex),     rs.getString(metaData.getColumnName(columnIndex)));
                        else
                            columnMap.put(metaData.getColumnLabel(columnIndex), "");
                    }
                    list.add(columnMap);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            json.put(keyName, list);
        }
        return JSONValue.toJSONString(json);
    }
}