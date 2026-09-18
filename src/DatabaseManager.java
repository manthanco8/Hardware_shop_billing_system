import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatabaseManager implements AutoCloseable {
    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Connection connection;

    public DatabaseManager() throws SQLException {
        this("jdbc:sqlite:hardware_shop.db");
    }

    // The URL constructor also makes the database class easy to test with an in-memory DB.
    public DatabaseManager(String jdbcUrl) throws SQLException {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Database URL cannot be empty.");
        }
        connection = DriverManager.getConnection(jdbcUrl);
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        createTables();
        seedProducts();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS products(" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, price REAL NOT NULL CHECK(price >= 0), stock INTEGER NOT NULL CHECK(stock >= 0))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS customers(" +
                    "phone TEXT PRIMARY KEY, name TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0 CHECK(balance >= 0))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transactions(" +
                    "id INTEGER PRIMARY KEY, customer_phone TEXT, customer_type TEXT NOT NULL, created_at TEXT NOT NULL, total REAL NOT NULL," +
                    "FOREIGN KEY(customer_phone) REFERENCES customers(phone))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transaction_items(" +
                    "transaction_id INTEGER NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity > 0), unit_price REAL NOT NULL," +
                    "PRIMARY KEY(transaction_id, product_id)," +
                    "FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(product_id) REFERENCES products(id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS settlements(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, customer_phone TEXT NOT NULL, amount REAL NOT NULL CHECK(amount > 0), settled_at TEXT NOT NULL," +
                    "FOREIGN KEY(customer_phone) REFERENCES customers(phone))");
        }
    }

    private void seedProducts() throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM products");
             ResultSet rs = check.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                String sql = "INSERT INTO products(id, name, price, stock) VALUES(?,?,?,?)";
                Object[][] products = {
                        {"P1", "Paint (1L)", 350.0, 40},
                        {"P2", "Paint Brush", 60.0, 100},
                        {"P3", "Cement Bag", 400.0, 25},
                        {"P4", "Nails (1kg)", 120.0, 60},
                        {"P5", "Sandpaper", 20.0, 200}
                };
                try (PreparedStatement insert = connection.prepareStatement(sql)) {
                    for (Object[] p : products) {
                        insert.setString(1, (String) p[0]);
                        insert.setString(2, (String) p[1]);
                        insert.setDouble(3, (Double) p[2]);
                        insert.setInt(4, (Integer) p[3]);
                        insert.executeUpdate();
                    }
                }
            }
        }
    }

    public Map<String, Product> loadProducts() throws SQLException {
        Map<String, Product> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, name, price, stock FROM products ORDER BY id");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Product product = new Product(rs.getString("id"), rs.getString("name"), rs.getDouble("price"), rs.getInt("stock"));
                result.put(product.getId(), product);
            }
        }
        return result;
    }

    public Map<String, CreditCustomer> loadCustomers() throws SQLException {
        Map<String, CreditCustomer> result = new HashMap<>();
        String sql = "SELECT phone, name, balance FROM customers ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                CreditCustomer customer = new CreditCustomer(rs.getString("name"), rs.getString("phone"));
                customer.setBalanceFromDatabase(rs.getDouble("balance"));
                result.put(customer.getPhone(), customer);
            }
        }
        return result;
    }

    public int getNextTransactionId() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(id), 1000) + 1 FROM transactions");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 1001;
        }
    }

    public Product findProduct(String id) throws ProductNotFoundException {
        if (id == null || id.isBlank()) throw new ProductNotFoundException("Product ID cannot be empty.");
        try {
            Map<String, Product> products = loadProducts();
            Product product = products.get(id.trim().toUpperCase());
            if (product == null) throw new ProductNotFoundException("Product not found: " + id);
            return product;
        } catch (SQLException e) {
            throw new ProductNotFoundException("Could not read product data: " + e.getMessage());
        }
    }

    // Overloaded lookup for a name search, demonstrating method overloading in a useful way.
    public List<Product> findProduct(String name, boolean byName) throws SQLException {
        List<Product> result = new ArrayList<>();
        if (!byName) {
            try {
                result.add(findProduct(name));
            } catch (ProductNotFoundException ignored) { }
            return result;
        }
        String sql = "SELECT id, name, price, stock FROM products WHERE LOWER(name) LIKE LOWER(?) ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + name.trim() + "%");
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new Product(rs.getString("id"), rs.getString("name"), rs.getDouble("price"), rs.getInt("stock")));
                }
            }
        }
        return result;
    }

    /**
     * Saves a sale atomically. Stock, transaction header, line items and credit balance
     * either all commit together or all roll back.
     */
    public void saveSale(Transaction transaction, String customerType, CreditCustomer customer)
            throws SQLException, ProductNotFoundException, InsufficientStockException {
        if (transaction == null) throw new IllegalArgumentException("Transaction cannot be null.");
        if (!"WALK_IN".equals(customerType) && !"CREDIT".equals(customerType)) {
            throw new IllegalArgumentException("Invalid customer type.");
        }
        if ("CREDIT".equals(customerType) && customer == null) {
            throw new IllegalArgumentException("Credit customer details are required.");
        }

        Set<String> productIds = new HashSet<>();
        for (BillItem item : transaction.getItems()) {
            if (!productIds.add(item.getProduct().getId())) {
                throw new IllegalArgumentException("A product cannot appear twice in the same bill.");
            }
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            if ("CREDIT".equals(customerType)) {
                String upsertCustomer = "INSERT INTO customers(phone, name, balance) VALUES(?,?,?) " +
                        "ON CONFLICT(phone) DO UPDATE SET name=excluded.name, balance=customers.balance + excluded.balance";
                try (PreparedStatement statement = connection.prepareStatement(upsertCustomer)) {
                    statement.setString(1, customer.getPhone());
                    statement.setString(2, customer.getName());
                    statement.setDouble(3, transaction.getTotal());
                    statement.executeUpdate();
                }
            }

            String transactionSql = "INSERT INTO transactions(id, customer_phone, customer_type, created_at, total) VALUES(?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(transactionSql)) {
                statement.setInt(1, transaction.getId());
                if ("CREDIT".equals(customerType)) statement.setString(2, customer.getPhone());
                else statement.setNull(2, Types.VARCHAR);
                statement.setString(3, customerType);
                statement.setString(4, DB_FORMATTER.format(transaction.getDate()));
                statement.setDouble(5, transaction.getTotal());
                statement.executeUpdate();
            }

            String stockSql = "SELECT stock FROM products WHERE id=?";
            String updateStockSql = "UPDATE products SET stock=stock-? WHERE id=? AND stock>=?";
            String itemSql = "INSERT INTO transaction_items(transaction_id, product_id, quantity, unit_price) VALUES(?,?,?,?)";

            try (PreparedStatement stockCheck = connection.prepareStatement(stockSql);
                 PreparedStatement updateStock = connection.prepareStatement(updateStockSql);
                 PreparedStatement insertItem = connection.prepareStatement(itemSql)) {
                for (BillItem item : transaction.getItems()) {
                    stockCheck.setString(1, item.getProduct().getId());
                    try (ResultSet rs = stockCheck.executeQuery()) {
                        if (!rs.next()) throw new ProductNotFoundException("Product not found: " + item.getProduct().getId());
                        int currentStock = rs.getInt(1);
                        if (item.getQuantity() > currentStock) {
                            throw new InsufficientStockException("Not enough stock for " + item.getProduct().getName() + ". Available: " + currentStock);
                        }
                    }

                    updateStock.setInt(1, item.getQuantity());
                    updateStock.setString(2, item.getProduct().getId());
                    updateStock.setInt(3, item.getQuantity());
                    if (updateStock.executeUpdate() != 1) {
                        throw new InsufficientStockException("Stock changed before the sale could be completed.");
                    }

                    insertItem.setInt(1, transaction.getId());
                    insertItem.setString(2, item.getProduct().getId());
                    insertItem.setInt(3, item.getQuantity());
                    insertItem.setDouble(4, item.getProduct().getPrice());
                    insertItem.executeUpdate();
                }
            }

            connection.commit();

            // Update only the Product objects already loaded by the application.
            // The database remains the source of truth for persisted stock and balance.
            for (BillItem item : transaction.getItems()) {
                item.getProduct().setStockFromDatabase(item.getProduct().getStock() - item.getQuantity());
            }
        } catch (SQLException | ProductNotFoundException | InsufficientStockException | RuntimeException e) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public String settleCustomer(String phone) throws SQLException, NoOutstandingBalanceException {
        String select = "SELECT name, balance FROM customers WHERE phone=?";
        String customerName;
        double balance;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, phone);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new NoOutstandingBalanceException("Customer not found.");
                customerName = rs.getString("name");
                balance = rs.getDouble("balance");
            }
        }
        if (balance <= 0.005) throw new NoOutstandingBalanceException(customerName + " has no outstanding balance.");

        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("UPDATE customers SET balance=0 WHERE phone=?")) {
                update.setString(1, phone);
                update.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO settlements(customer_phone, amount, settled_at) VALUES(?,?,?)")) {
                insert.setString(1, phone);
                insert.setDouble(2, balance);
                insert.setString(3, DB_FORMATTER.format(LocalDateTime.now()));
                insert.executeUpdate();
            }
            connection.commit();
            return String.format("Account settled for %s. Amount paid: Rs.%.2f", customerName, balance);
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public List<String> recentTransactions(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT t.id, t.created_at, t.customer_type, COALESCE(c.name, 'Walk-in') AS customer_name, t.total " +
                "FROM transactions t LEFT JOIN customers c ON t.customer_phone=c.phone ORDER BY t.id DESC LIMIT ?";
        List<String> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(String.format("#%d | %s | %-18s | %-7s | Rs.%.2f",
                            rs.getInt("id"), rs.getString("created_at"), rs.getString("customer_name"),
                            rs.getString("customer_type"), rs.getDouble("total")));
                }
            }
        }
        return rows;
    }

    @Override
    public void close() throws SQLException {
        if (!connection.isClosed()) connection.close();
    }
}
