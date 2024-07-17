import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.ExportException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.io.StringWriter;
import java.io.FileWriter;
import java.io.IOException;

public class GlobalLocks {

    public static void main(String[] args) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        executor.submit(GlobalLocks::monitorMySQLLocks);
        executor.submit(GlobalLocks::monitorMSSQLLocks);
        executor.submit(GlobalLocks::monitorPostgreSQLLocks);
    }

    private static void monitorMySQLLocks() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT r.trx_id AS waiting_trx_id, r.trx_mysql_thread_id AS waiting_thread, " +
                                     "b.trx_id AS blocking_trx_id, b.trx_mysql_thread_id AS blocking_thread, " +
                                     "r.trx_requested_lock_id AS waiting_lock, b.trx_requested_lock_id AS blocking_lock " +
                                     "FROM information_schema.innodb_lock_waits w " +
                                     "JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id " +
                                     "JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id")) {

                    while (rs.next()) {
                        String waitingThread = rs.getString("waiting_thread");
                        String blockingThread = rs.getString("blocking_thread");
                        String waitingLock = rs.getString("waiting_lock");
                        String blockingLock = rs.getString("blocking_lock");

                        graph.addVertex(waitingThread + " (Lock: " + waitingLock + ")");
                        graph.addVertex(blockingThread + " (Lock: " + blockingLock + ")");
                        graph.addEdge(waitingThread + " (Lock: " + waitingLock + ")", blockingThread + " (Lock: " + blockingLock + ")");
                    }
                }
                exportGraphToDOT(graph, "mysql_lock_graph.dot");
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void monitorMSSQLLocks() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlserver://localhost;databaseName=yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT waiting.request_session_id AS waiting_session_id, " +
                                     "blocking.blocking_session_id AS blocking_session_id, " +
                                     "waiting.resource_type AS waiting_resource, blocking.resource_type AS blocking_resource " +
                                     "FROM sys.dm_tran_locks waiting " +
                                     "JOIN sys.dm_tran_locks blocking ON waiting.lock_owner_address = blocking.lock_owner_address " +
                                     "WHERE waiting.request_status = 'WAIT'")) {

                    while (rs.next()) {
                        String waitingSession = rs.getString("waiting_session_id");
                        String blockingSession = rs.getString("blocking_session_id");
                        String waitingResource = rs.getString("waiting_resource");
                        String blockingResource = rs.getString("blocking_resource");

                        graph.addVertex(waitingSession + " (Resource: " + waitingResource + ")");
                        graph.addVertex(blockingSession + " (Resource: " + blockingResource + ")");
                        graph.addEdge(waitingSession + " (Resource: " + waitingResource + ")", blockingSession + " (Resource: " + blockingResource + ")");
                    }
                }
                exportGraphToDOT(graph, "mssql_lock_graph.dot");
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void monitorPostgreSQLLocks() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/yourdb", "username", "password")) {
            while (true) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT w.pid AS waiting_pid, l.pid AS blocking_pid, " +
                                     "w.locktype AS waiting_lock, l.locktype AS blocking_lock " +
                                     "FROM pg_locks l " +
                                     "JOIN pg_locks w ON w.locktype = l.locktype " +
                                     "AND w.database IS NOT DISTINCT FROM l.database " +
                                     "AND w.relation IS NOT DISTINCT FROM l.relation " +
                                     "AND w.page IS NOT DISTINCT FROM l.page " +
                                     "AND w.tuple IS NOT DISTINCT FROM l.tuple " +
                                     "AND w.virtualxid IS NOT DISTINCT FROM l.virtualxid " +
                                     "AND w.transactionid IS NOT DISTINCT FROM l.transactionid " +
                                     "AND w.classid IS NOT DISTINCT FROM l.classid " +
                                     "AND w.objid IS NOT DISTINCT FROM l.objid " +
                                     "AND w.objsubid IS NOT DISTINCT FROM l.objsubid " +
                                     "AND w.pid <> l.pid " +
                                     "JOIN pg_stat_activity a ON a.pid = l.pid " +
                                     "JOIN pg_stat_activity b ON b.pid = w.pid " +
                                     "WHERE NOT l.granted")) {

                    while (rs.next()) {
                        String waitingPid = rs.getString("waiting_pid");
                        String blockingPid = rs.getString("blocking_pid");
                        String waitingLock = rs.getString("waiting_lock");
                        String blockingLock = rs.getString("blocking_lock");

                        graph.addVertex(waitingPid + " (Lock: " + waitingLock + ")");
                        graph.addVertex(blockingPid + " (Lock: " + blockingLock + ")");
                        graph.addEdge(waitingPid + " (Lock: " + waitingLock + ")", blockingPid + " (Lock: " + blockingLock + ")");
                    }
                }
                exportGraphToDOT(graph, "postgresql_lock_graph.dot");
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void exportGraphToDOT(Graph<String, DefaultEdge> graph, String filename) {
        DOTExporter<String, DefaultEdge> exporter = new DOTExporter<>();
        exporter.setVertexAttributeProvider(v -> {
            Map<String, Attribute> map = new HashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v));
            return map;
        });

        try (StringWriter writer = new StringWriter()) {
            exporter.exportGraph(graph, writer);
            try (FileWriter fileWriter = new FileWriter(filename)) {
                fileWriter.write(writer.toString());
            }
        } catch (ExportException | IOException e) {
            e.printStackTrace();
        }
    }
}
