import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class WalkInCustomer extends Customer {

    public WalkInCustomer(String name) {
        super(name, null);
    }

    @Override
    public void recordPurchase(Transaction transaction) {
        String fileName = "bill_" + name.replace(" ", "_") + "_" + System.currentTimeMillis() + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("===== HARDWARE SHOP BILL =====\n");
            writer.write("Customer: " + name + "\n");
            writer.write(transaction.toReceiptLine());
            writer.write("Paid in full at counter.\n");
            System.out.println("Bill saved as file: " + fileName);
        } catch (IOException e) {
            System.out.println("Could not save bill file: " + e.getMessage());
        }

        // Also log the sale in the database (Unit 5: JDBC) so walk-in sales
        // show up in the shop's permanent transaction history, even though
        // walk-ins have no ongoing account. Paid in full -> settled = true.
        String txnDate = String.valueOf(transaction.getDate().getTime());
        int txnId = DatabaseManager.insertTransaction(null, name, "WALKIN",
                txnDate, transaction.getTotal(), true);
        if (txnId != -1) {
            for (BillItem item : transaction.getItems()) {
                DatabaseManager.insertTransactionItem(txnId, item.getProduct().getId(),
                        item.getProduct().getName(), item.getQuantity(),
                        item.getProduct().getPrice(), item.getSubtotal());
            }
        }
    }
}
