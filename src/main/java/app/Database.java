package app;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

// credit: https://docs.microsoft.com/en-us/sql/connect/jdbc/step-3-proof-of-concept-connecting-to-sql-using-java?view=sql-server-ver15
public class Database {

    private static Connection connection = null;

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        String url = "jdbc:mysql://digidata.czuuwxiu54rk.us-east-1.rds.amazonaws.com:3306";
        try {
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

    public static int statement(String query) throws SQLException, ClassNotFoundException {
        return Database.getConnection().prepareStatement(query).executeUpdate();
    }

    // https://stackoverflow.com/questions/17160351/create-json-object-by-java-from-data-of-mysql\
    // credit to original author, multiple bugs fixed
    public static String getJSONFromResultSet(ResultSet rs, String keyName) {
        HashMap<String,Object> json = new HashMap<>();
        ArrayList<Map<String, Object>> list = new ArrayList<>();
        if (rs != null) {
            try {
                ResultSetMetaData metaData = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> columnMap = new HashMap<String, Object>();
                    for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
                        if (rs.getString(columnIndex) != null)
                            columnMap.put(metaData.getColumnLabel(columnIndex),
                                    rs.getString(columnIndex));
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