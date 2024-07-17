import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class LastExecutedSQLStatements {

    public static void main(String[] args) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        executor.submit(LastExecutedSQLStatements::monitorMySQLLocks);
        executor.submit(LastExecutedSQLStatements::monitorMSSQLLocks);
        executor.submit(LastExecutedSQLStatements::monitorPostgreSQLLocks);
    }

    private static void monitorMySQLLocks() {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT " +
                                     "    ps.processlist_id AS id, " +
                                     "    ps.processlist_user AS user, " +
                                     "    ps.processlist_host AS host, " +
                                     "    ps.processlist_db AS db, " +
                                     "    ps.processlist_command AS command, " +
                                     "    ps.processlist_time AS time, " +
                                     "    ps.processlist_state AS state, " +
                                     "    ps.processlist_info AS info " +
                                     "FROM " +
                                     "    performance_schema.threads ps " +
                                     "WHERE " +
                                     "    ps.processlist_command NOT IN ('Sleep', 'Query') " +
                                     "ORDER BY " +
                                     "    ps.processlist_time DESC")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String user = rs.getString("user");
                        String host = rs.getString("host");
                        String db = rs.getString("db");
                        String command = rs.getString("command");
                        int time = rs.getInt("time");
                        String state = rs.getString("state");
                        String info = rs.getString("info");
                        System.out.printf("ID: %d, User: %s, Host: %s, DB: %s, Command: %s, Time: %d, State: %s, Info: %s%n",
                                id, user, host, db, command, time, state, info);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void monitorMSSQLLocks() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlserver://localhost;databaseName=yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT " +
                                     "    r.session_id, " +
                                     "    r.status, " +
                                     "    r.wait_type, " +
                                     "    r.wait_time, " +
                                     "    r.wait_resource, " +
                                     "    r.blocking_session_id, " +
                                     "    t.text AS last_sql " +
                                     "FROM " +
                                     "    sys.dm_exec_requests r " +
                                     "CROSS APPLY " +
                                     "    sys.dm_exec_sql_text(r.sql_handle) t " +
                                     "WHERE " +
                                     "    r.command NOT IN ('SELECT', 'EXECUTE')")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        int sessionId = rs.getInt("session_id");
                        String status = rs.getString("status");
                        String waitType = rs.getString("wait_type");
                        int waitTime = rs.getInt("wait_time");
                        String waitResource = rs.getString("wait_resource");
                        int blockingSessionId = rs.getInt("blocking_session_id");
                        String lastSql = rs.getString("last_sql");
                        System.out.printf("Session ID: %d, Status: %s, Wait Type: %s, Wait Time: %d, Wait Resource: %s, Blocking Session ID: %d, Last SQL: %s%n",
                                sessionId, status, waitType, waitTime, waitResource, blockingSessionId, lastSql);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void monitorPostgreSQLLocks() {
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT " +
                                     "    pid, " +
                                     "    usename, " +
                                     "    application_name, " +
                                     "    client_addr, " +
                                     "    state, " +
                                     "    query " +
                                     "FROM " +
                                     "    pg_stat_activity " +
                                     "WHERE " +
                                     "    state != 'active' " +
                                     "ORDER BY " +
                                     "    backend_start DESC")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        int pid = rs.getInt("pid");
                        String usename = rs.getString("usename");
                        String applicationName = rs.getString("application_name");
                        String clientAddr = rs.getString("client_addr");
                        String state = rs.getString("state");
                        String query = rs.getString("query");
                        System.out.printf("PID: %d, User: %s, Application Name: %s, Client Addr: %s, State: %s, Query: %s%n",
                                pid, usename, applicationName, clientAddr, state, query);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
