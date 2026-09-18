import java.util.List;
import java.util.Map;

/** Small end-to-end check for the JDBC part of the project. */
public class SystemTest {
    public static void main(String[] args) throws Exception {
        try (DatabaseManager db = new DatabaseManager("jdbc:sqlite::memory:")) {
            Map<String, Product> products = db.loadProducts();
            int startingStock = products.get("P2").getStock();

            CreditCustomer customer = new CreditCustomer("Ravi Kumar", "8888888888");
            Transaction goodSale = new Transaction(
                    2001,
                    List.of(new BillItem(products.get("P2"), 3), new BillItem(products.get("P5"), 2)));

            db.saveSale(goodSale, "CREDIT", customer);

            Map<String, Product> afterSale = db.loadProducts();
            if (afterSale.get("P2").getStock() != startingStock - 3) {
                throw new AssertionError("Stock was not persisted correctly");
            }

            Map<String, CreditCustomer> savedCustomers = db.loadCustomers();
            CreditCustomer saved = savedCustomers.get("8888888888");
            double expectedDue = goodSale.getTotal();
            if (saved == null || Math.abs(saved.getBalance() - expectedDue) > 0.001) {
                throw new AssertionError("Credit balance was not persisted correctly");
            }

            Product freshP2 = afterSale.get("P2");
            Transaction badSale = new Transaction(
                    2002,
                    List.of(new BillItem(freshP2, freshP2.getStock() + 1)));
            try {
                db.saveSale(badSale, "WALK_IN", null);
                throw new AssertionError("Over-stock sale should have failed");
            } catch (InsufficientStockException expected) {
                // Expected. The important part is that the database rolls the sale back.
            }

            Map<String, Product> afterRollback = db.loadProducts();
            if (afterRollback.get("P2").getStock() != startingStock - 3) {
                throw new AssertionError("Rollback changed persisted stock");
            }
            if (db.recentTransactions(10).size() != 1) {
                throw new AssertionError("Failed sale was partially persisted");
            }

            db.settleCustomer("8888888888");
            if (db.loadCustomers().get("8888888888").hasDue()) {
                throw new AssertionError("Settlement did not clear the balance");
            }
            if (!db.recentTransactions(10).get(0).contains("CREDIT")) {
                throw new AssertionError("Transaction history was not retained");
            }

            System.out.println("End-to-end JDBC tests passed.");
        }
    }
}
