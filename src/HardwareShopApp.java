import java.sql.SQLException;
import java.util.*;

public class HardwareShopApp implements AutoCloseable {
    private final Scanner scanner = new Scanner(System.in);
    private final DatabaseManager database;
    private final Map<String, Product> inventory;
    private final Map<String, CreditCustomer> customers;
    private final ReceiptWriter receiptWriter;
    private int transactionCounter;

    private HardwareShopApp() throws SQLException {
        database = new DatabaseManager();
        inventory = database.loadProducts();
        customers = database.loadCustomers();
        transactionCounter = database.getNextTransactionId();
        receiptWriter = new FileReceiptWriter("bills");
    }

    private void displayProducts() {
        System.out.println("\nID    PRODUCT                    PRICE      STOCK");
        inventory.values().forEach(System.out::println);
    }

    private void searchProducts() {
        System.out.print("Product name: ");
        String name = scanner.nextLine().trim();
        if (name.isBlank()) {
            System.out.println("Product name cannot be empty.");
            return;
        }
        try {
            List<Product> matches = database.findProduct(name, true);
            if (matches.isEmpty()) {
                System.out.println("No matching products found.");
                return;
            }
            System.out.println("\nMATCHING PRODUCTS");
            matches.forEach(System.out::println);
        } catch (SQLException e) {
            System.out.println("Could not search products: " + e.getMessage());
        }
    }

    private List<BillItem> collectItems() {
        List<BillItem> items = new ArrayList<>();
        displayProducts();
        while (true) {
            System.out.print("Product ID (done to finish): ");
            String id = scanner.nextLine().trim().toUpperCase();
            if (id.equals("DONE")) break;

            Product product;
            try {
                product = inventory.get(id);
                if (product == null) throw new ProductNotFoundException("Product not found: " + id);
                System.out.print("Quantity: ");
                int quantity = Integer.parseInt(scanner.nextLine().trim());
                if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive.");
                int existingQuantity = 0;
                for (BillItem existing : items) {
                    if (existing.getProduct().getId().equals(product.getId())) {
                        existingQuantity = existing.getQuantity();
                        break;
                    }
                }
                int combinedQuantity = existingQuantity + quantity;
                if (combinedQuantity > product.getStock()) {
                    throw new InsufficientStockException("Not enough stock for " + product.getName() + ". Available: " + product.getStock());
                }
                if (existingQuantity > 0) {
                    items.removeIf(existing -> existing.getProduct().getId().equals(product.getId()));
                }
                items.add(new BillItem(product, combinedQuantity));
                System.out.println("Item added.");
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid whole number.");
            } catch (ProductNotFoundException | InsufficientStockException | IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
        return items;
    }

    private CreditCustomer getOrCreateCustomer() {
        System.out.print("Phone number: ");
        String phone = scanner.nextLine().trim();
        CreditCustomer existing = customers.get(phone);
        if (existing != null) return existing;

        System.out.print("Customer name: ");
        String name = scanner.nextLine().trim();
        return new CreditCustomer(name, phone);
    }

    private void writeReceiptInWorkerThread(Transaction transaction, String customerName) throws Exception {
        ReceiptTask task = new ReceiptTask(receiptWriter, transaction, customerName);
        Thread worker = new Thread(task, "receipt-worker-" + transaction.getId());
        worker.start();
        worker.join();
        System.out.println("Bill saved to " + task.getResult());
    }

    private void newSale() {
        System.out.print("Customer type (1-Walk-in, 2-Khata): ");
        String type = scanner.nextLine().trim();
        String customerType;
        CreditCustomer creditCustomer = null;
        String customerName;

        try {
            if (type.equals("1")) {
                customerType = "WALK_IN";
                System.out.print("Customer name: ");
                customerName = scanner.nextLine().trim();
                if (customerName.isBlank()) throw new IllegalArgumentException("Customer name cannot be empty.");
            } else if (type.equals("2")) {
                customerType = "CREDIT";
                creditCustomer = getOrCreateCustomer();
                customerName = creditCustomer.getName();
            } else {
                System.out.println("Invalid customer type. Sale cancelled before stock is changed.");
                return;
            }

            List<BillItem> items = collectItems();
            if (items.isEmpty()) {
                System.out.println("No items selected. Sale cancelled.");
                return;
            }

            Transaction transaction = new Transaction(transactionCounter++, items);
            database.saveSale(transaction, customerType, creditCustomer);
            if (creditCustomer != null) {
                creditCustomer.processTransaction(transaction);
                customers.put(creditCustomer.getPhone(), creditCustomer);
            }
            writeReceiptInWorkerThread(transaction, customerName);
            System.out.println("\n" + transaction.receipt());
        } catch (IllegalArgumentException | SQLException | ProductNotFoundException | InsufficientStockException e) {
            System.out.println("Sale could not be completed: " + e.getMessage());
            // Do not reuse an ID that was not persisted.
            try { transactionCounter = database.getNextTransactionId(); } catch (SQLException ignored) { }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Receipt worker was interrupted.");
        } catch (Exception e) {
            System.out.println("Unexpected error while generating the receipt: " + e.getMessage());
        }
    }

    private void settleAccount() {
        System.out.print("Phone number: ");
        String phone = scanner.nextLine().trim();
        CreditCustomer customer = customers.get(phone);
        if (customer == null) { System.out.println("Customer not found."); return; }
        try {
            String message = database.settleCustomer(phone);
            customer.settleInMemory();
            System.out.println(message);
        } catch (NoOutstandingBalanceException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Could not settle account: " + e.getMessage());
        }
    }

    private void showBalances() {
        System.out.println("\nOUTSTANDING BALANCES");
        customers.clear();
        try {
            customers.putAll(database.loadCustomers());
        } catch (SQLException e) {
            System.out.println("Could not load balances: " + e.getMessage());
            return;
        }

        boolean found = false;
        for (CreditCustomer customer : customers.values()) {
            if (customer.hasDue()) {
                System.out.printf("%-20s %-15s Rs.%.2f%n", customer.getName(), customer.getPhone(), customer.getBalance());
                found = true;
            }
        }
        if (!found) System.out.println("No outstanding balances.");
    }

    private void showRecentTransactions() {
        System.out.println("\nRECENT TRANSACTIONS");
        try {
            List<String> rows = database.recentTransactions(10);
            if (rows.isEmpty()) System.out.println("No transactions found.");
            else rows.forEach(System.out::println);
        } catch (SQLException e) {
            System.out.println("Could not load transactions: " + e.getMessage());
        }
    }

    @Override
    public void close() throws SQLException {
        scanner.close();
        database.close();
    }

    private void run() {
        while (true) {
            System.out.println("\n===== HARDWARE SHOP BILLING SYSTEM =====");
            System.out.println("1. New sale");
            System.out.println("2. Settle khata account");
            System.out.println("3. View stock");
            System.out.println("4. View balances");
            System.out.println("5. View recent transactions");
            System.out.println("6. Search product by name");
            System.out.println("7. Exit");
            System.out.print("Choose: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> newSale();
                case "2" -> settleAccount();
                case "3" -> displayProducts();
                case "4" -> showBalances();
                case "5" -> showRecentTransactions();
                case "6" -> searchProducts();
                case "7" -> { System.out.println("Thank you."); return; }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    public static void main(String[] args) {
        try (HardwareShopApp app = new HardwareShopApp()) {
            app.run();
        } catch (SQLException e) {
            System.out.println("Application startup failed: " + e.getMessage());
            System.out.println("Make sure the SQLite JDBC driver is available in the classpath.");
        }
    }
}
