import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit 5 (JDBC) implementation.
 *
 * Everything the shop needs to remember across runs — inventory, khata
 * customers, every purchase and every settlement — is persisted to a local
 * SQLite database file (hardware_shop.db) via plain java.sql JDBC calls:
 * Connection, PreparedStatement, ResultSet.
 *
 * SQLite (not MySQL) was chosen deliberately: it needs no separate database
 * server to install or configure, so this project runs out of the box on
 * any machine with just the JDBC driver jar on the classpath.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:hardware_shop.db";
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
        }
        return connection;
    }

    /** Creates all tables if this is the first run. Safe to call every startup. */
    public static void initSchema() {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS products (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "price REAL NOT NULL," +
                "stock INTEGER NOT NULL)",

            "CREATE TABLE IF NOT EXISTS customers (" +
                "phone TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "outstanding_balance REAL NOT NULL DEFAULT 0)",

            "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "customer_phone TEXT," +               // NULL for walk-in sales
                "customer_name TEXT NOT NULL," +
                "customer_type TEXT NOT NULL," +        // 'PERMANENT' or 'WALKIN'
                "txn_date TEXT NOT NULL," +              // epoch millis, as text
                "total REAL NOT NULL," +
                "settled INTEGER NOT NULL DEFAULT 0)",

            "CREATE TABLE IF NOT EXISTS transaction_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "transaction_id INTEGER NOT NULL," +
                "product_id TEXT NOT NULL," +
                "product_name TEXT NOT NULL," +
                "quantity INTEGER NOT NULL," +
                "unit_price REAL NOT NULL," +
                "subtotal REAL NOT NULL)",

            // Audit trail: settled khata accounts stay here permanently,
            // even though pendingTransactions is cleared in memory/marked
            // settled in the transactions table.
            "CREATE TABLE IF NOT EXISTS settlements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "customer_phone TEXT NOT NULL," +
                "customer_name TEXT NOT NULL," +
                "settled_date TEXT NOT NULL," +
                "total_amount REAL NOT NULL)"
        };

        try (Statement st = getConnection().createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            System.out.println("Database init failed: " + e.getMessage());
        }
    }

    /** First run only: seed the same 5 sample products the prototype shipped with. */
    public static void seedInventoryIfEmpty() {
        String countSql = "SELECT COUNT(*) AS c FROM products";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(countSql)) {
            if (rs.next() && rs.getInt("c") == 0) {
                Object[][] sample = {
                    {"P1", "Paint (1L)", 350.0, 40},
                    {"P2", "Paint Brush", 60.0, 100},
                    {"P3", "Cement Bag (50kg)", 400.0, 25},
                    {"P4", "Nails (1kg)", 120.0, 60},
                    {"P5", "Sandpaper Sheet", 20.0, 200}
                };
                String ins = "INSERT INTO products(id, name, price, stock) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = getConnection().prepareStatement(ins)) {
                    for (Object[] row : sample) {
                        ps.setString(1, (String) row[0]);
                        ps.setString(2, (String) row[1]);
                        ps.setDouble(3, (Double) row[2]);
                        ps.setInt(4, (Integer) row[3]);
                        ps.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Seeding inventory failed: " + e.getMessage());
        }
    }

    public static Map<String, Product> loadAllProducts() {
        Map<String, Product> map = new HashMap<>();
        String sql = "SELECT id, name, price, stock FROM products";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("id"), new Product(
                        rs.getString("id"), rs.getString("name"),
                        rs.getDouble("price"), rs.getInt("stock")));
            }
        } catch (SQLException e) {
            System.out.println("Loading inventory failed: " + e.getMessage());
        }
        return map;
    }

    public static void updateProductStock(String id, int newStock) {
        String sql = "UPDATE products SET stock = ? WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Updating stock failed: " + e.getMessage());
        }
    }

    public static void upsertCustomer(String phone, String name, double balance) {
        String sql = "INSERT INTO customers(phone, name, outstanding_balance) VALUES (?, ?, ?) " +
                     "ON CONFLICT(phone) DO UPDATE SET name = excluded.name, " +
                     "outstanding_balance = excluded.outstanding_balance";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, name);
            ps.setDouble(3, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Saving customer failed: " + e.getMessage());
        }
    }

    /** phone == null for a walk-in sale (walk-ins have no persistent account). */
    public static int insertTransaction(String phone, String name, String type,
                                         String date, double total, boolean settled) {
        String sql = "INSERT INTO transactions(customer_phone, customer_name, customer_type, " +
                     "txn_date, total, settled) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (phone == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, phone);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setString(4, date);
            ps.setDouble(5, total);
            ps.setInt(6, settled ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("Saving transaction failed: " + e.getMessage());
        }
        return -1;
    }

    public static void insertTransactionItem(int transactionId, String productId, String productName,
                                              int qty, double unitPrice, double subtotal) {
        String sql = "INSERT INTO transaction_items(transaction_id, product_id, product_name, " +
                     "quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            ps.setString(2, productId);
            ps.setString(3, productName);
            ps.setInt(4, qty);
            ps.setDouble(5, unitPrice);
            ps.setDouble(6, subtotal);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Saving transaction item failed: " + e.getMessage());
        }
    }

    private static List<BillItem> loadItemsForTransaction(int transactionId) {
        List<BillItem> items = new ArrayList<>();
        String sql = "SELECT product_id, product_name, quantity, unit_price " +
                     "FROM transaction_items WHERE transaction_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Snapshot Product rebuilt from the stored price at time of
                    // purchase, so a later price change never alters old bills.
                    Product snapshot = new Product(rs.getString("product_id"),
                            rs.getString("product_name"), rs.getDouble("unit_price"), 0);
                    items.add(new BillItem(snapshot, rs.getInt("quantity")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Loading transaction items failed: " + e.getMessage());
        }
        return items;
    }

    private static List<Transaction> loadPendingTransactions(String phone) {
        List<Transaction> result = new ArrayList<>();
        String sql = "SELECT id, txn_date FROM transactions " +
                     "WHERE customer_phone = ? AND settled = 0 ORDER BY id";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int txnId = rs.getInt("id");
                    Date date = new Date(Long.parseLong(rs.getString("txn_date")));
                    List<BillItem> items = loadItemsForTransaction(txnId);
                    result.add(new Transaction(items, date, txnId));
                }
            }
        } catch (SQLException e) {
            System.out.println("Loading pending transactions failed: " + e.getMessage());
        }
        return result;
    }

    /** Reloads every khata customer, each with their unsettled purchases reattached. */
    public static Map<String, PermanentCustomer> loadAllCustomers() {
        Map<String, PermanentCustomer> customers = new HashMap<>();
        String sql = "SELECT phone, name, outstanding_balance FROM customers";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String phone = rs.getString("phone");
                String name = rs.getString("name");
                double balance = rs.getDouble("outstanding_balance");
                List<Transaction> pending = loadPendingTransactions(phone);
                customers.put(phone, new PermanentCustomer(name, phone, balance, pending));
            }
        } catch (SQLException e) {
            System.out.println("Loading customers failed: " + e.getMessage());
        }
        return customers;
    }

    public static void markTransactionsSettled(String phone) {
        String sql = "UPDATE transactions SET settled = 1 WHERE customer_phone = ? AND settled = 0";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Marking transactions settled failed: " + e.getMessage());
        }
    }

    public static void insertSettlement(String phone, String name, String date, double totalAmount) {
        String sql = "INSERT INTO settlements(customer_phone, customer_name, settled_date, total_amount) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, name);
            ps.setString(3, date);
            ps.setDouble(4, totalAmount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Saving settlement record failed: " + e.getMessage());
        }
    }

    /** Full audit trail of every khata account ever settled, newest first. */
    public static List<String> loadSettlementHistory() {
        List<String> lines = new ArrayList<>();
        String sql = "SELECT customer_name, customer_phone, settled_date, total_amount " +
                     "FROM settlements ORDER BY id DESC";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            SimpleDateFormatHolder fmt = new SimpleDateFormatHolder();
            while (rs.next()) {
                String when = fmt.format(Long.parseLong(rs.getString("settled_date")));
                lines.add(String.format("%-20s %-15s %-18s Rs.%.2f",
                        rs.getString("customer_name"), rs.getString("customer_phone"),
                        when, rs.getDouble("total_amount")));
            }
        } catch (SQLException e) {
            System.out.println("Loading settlement history failed: " + e.getMessage());
        }
        return lines;
    }

    // Tiny helper so we don't need a formatter field per call in the loop above.
    private static class SimpleDateFormatHolder {
        private final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm");
        String format(long epochMillis) { return sdf.format(new Date(epochMillis)); }
    }

    public static void closeConnection() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
            // nothing useful to do on shutdown
        }
    }
}
