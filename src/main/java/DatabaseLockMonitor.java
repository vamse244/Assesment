import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class DatabaseLockMonitor {

    public static void main(String[] args) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        executor.submit(() -> monitorMySQLLocks());
        executor.submit(() -> monitorMSSQLLocks());
        executor.submit(() -> monitorPostgreSQLLocks());
    }

    private static void monitorMySQLLocks() {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        String status = rs.getString("Status");
                        // Parse the status to find locks (implementation needed)
                        System.out.println(status);
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
                     ResultSet rs = stmt.executeQuery("SELECT * FROM sys.dm_exec_requests")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        int sessionId = rs.getInt("session_id");
                        String status = rs.getString("status");
                        // Extract more details as needed
                        System.out.println("Session ID: " + sessionId + ", Status: " + status);
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
                     ResultSet rs = stmt.executeQuery("SELECT * FROM pg_locks")) {

                    // Process the result set to extract lock information
                    while (rs.next()) {
                        int pid = rs.getInt("pid");
                        String mode = rs.getString("mode");
                        // Extract more details as needed
                        System.out.println("PID: " + pid + ", Mode: " + mode);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
