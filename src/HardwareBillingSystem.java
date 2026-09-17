import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HardwareBillingSystem {

    private Map<String, Product> inventory;
    private Map<String, PermanentCustomer> permanentCustomers;
    private Scanner scanner = new Scanner(System.in);

    /**
     * Sets up the SQLite database (Unit 5: JDBC), seeds sample inventory on
     * the very first run, then loads inventory + every khata customer's
     * unsettled purchases back into memory. This replaces the old
     * loadSampleInventory() that always started from a blank slate.
     */
    public void initializeSystem() {
        DatabaseManager.initSchema();
        DatabaseManager.seedInventoryIfEmpty();
        inventory = DatabaseManager.loadAllProducts();
        permanentCustomers = DatabaseManager.loadAllCustomers();
    }

    public Product findProduct(String id) throws ProductNotFoundException {
        Product p = inventory.get(id);
        if (p == null) {
            throw new ProductNotFoundException("No product with id: " + id);
        }
        return p;
    }

    public void checkStock(Product p, int qty) throws InsufficientStockException {
        if (p.getStock() < qty) {
            throw new InsufficientStockException("Only " + p.getStock() + " units left for " + p.getName());
        }
    }

    public void showMenu() {
        System.out.println("\n--- AVAILABLE PRODUCTS ---");
        for (Product p : inventory.values()) {
            System.out.printf("%-4s %-18s Rs.%-6.2f Stock:%d%n",
                    p.getId(), p.getName(), p.getPrice(), p.getStock());
        }
    }

    private List<BillItem> takeOrder() {
        List<BillItem> items = new ArrayList<>();
        showMenu();
        while (true) {
            System.out.print("\nEnter product ID to add (or 'done' to finish): ");
            String id = scanner.nextLine().trim();
            if (id.equalsIgnoreCase("done")) break;
            try {
                Product p = findProduct(id);
                System.out.print("Enter quantity: ");
                int qty = Integer.parseInt(scanner.nextLine().trim());
                if (qty <= 0) {
                    System.out.println("Quantity must be positive.");
                    continue;
                }
                checkStock(p, qty);
                items.add(new BillItem(p, qty));
                p.reduceStock(qty);
                DatabaseManager.updateProductStock(p.getId(), p.getStock()); // JDBC write-through
                System.out.println("Added: " + p.getName() + " x" + qty);
            } catch (ProductNotFoundException | InsufficientStockException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
        return items;
    }

    public void newPurchase() {
        System.out.print("\nIs this a permanent (khata) customer? (y/n): ");
        String isPermanent = scanner.nextLine().trim().toLowerCase();

        if (isPermanent.equals("y")) {
            System.out.print("Enter phone number: ");
            String phone = scanner.nextLine().trim();
            PermanentCustomer customer = permanentCustomers.get(phone);
            if (customer == null) {
                System.out.print("New customer. Enter name: ");
                String name = scanner.nextLine().trim();
                customer = new PermanentCustomer(name, phone);
                permanentCustomers.put(phone, customer);
            } else {
                System.out.println("Welcome back, " + customer.getName() + "!");
            }

            List<BillItem> items = takeOrder();
            if (!items.isEmpty()) {
                customer.recordPurchase(new Transaction(items));
            }
        } else {
            System.out.print("Enter customer name: ");
            String name = scanner.nextLine().trim();
            WalkInCustomer customer = new WalkInCustomer(name);

            List<BillItem> items = takeOrder();
            if (!items.isEmpty()) {
                customer.recordPurchase(new Transaction(items));
            }
        }
    }

    public void settleAccount() {
        System.out.print("\nEnter phone number of customer settling their account: ");
        String phone = scanner.nextLine().trim();
        PermanentCustomer customer = permanentCustomers.get(phone);
        if (customer == null) {
            System.out.println("No such khata customer found.");
            return;
        }
        try {
            String statement = customer.settleAccount();
            System.out.println("\n" + statement);
        } catch (NoOutstandingDueException e) {
            System.out.println(e.getMessage());
        }
    }

    public void viewOutstanding() {
        System.out.println("\n--- OUTSTANDING KHATA CUSTOMERS ---");
        boolean any = false;
        for (PermanentCustomer c : permanentCustomers.values()) {
            if (c.getOutstandingBalance() > 0) {
                System.out.printf("%-20s %-15s Rs.%.2f%n", c.getName(), c.getPhoneNumber(), c.getOutstandingBalance());
                any = true;
            }
        }
        if (!any) {
            System.out.println("No pending dues right now.");
        }
    }

    /** New in this version: full audit trail, read straight from the database (Unit 5: JDBC). */
    public void viewSettlementHistory() {
        System.out.println("\n--- SETTLEMENT HISTORY (audit trail) ---");
        List<String> history = DatabaseManager.loadSettlementHistory();
        if (history.isEmpty()) {
            System.out.println("No accounts have been settled yet.");
            return;
        }
        for (String line : history) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) {
        HardwareBillingSystem system = new HardwareBillingSystem();
        system.initializeSystem();

        System.out.println("===== HARDWARE SHOP BILLING SYSTEM =====");

        while (true) {
            System.out.println("\n1. New purchase");
            System.out.println("2. Settle a khata account");
            System.out.println("3. View outstanding customers");
            System.out.println("4. View settlement history");
            System.out.println("5. Exit");
            System.out.print("Choose: ");

            String choice = system.scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    system.newPurchase();
                    break;
                case "2":
                    system.settleAccount();
                    break;
                case "3":
                    system.viewOutstanding();
                    break;
                case "4":
                    system.viewSettlementHistory();
                    break;
                case "5":
                    System.out.println("Shop closed. Thank you!");
                    DatabaseManager.closeConnection();
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }
}
