import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ValidationTest {
    public static void main(String[] args) throws Exception {
        Product product = new Product("T1", "Test Product", 10.0, 5);

        try { product.reduceStock(6); throw new AssertionError("Stock validation failed"); }
        catch (InsufficientStockException expected) { }

        try { product.reduceStock(0); throw new AssertionError("Quantity validation failed"); }
        catch (IllegalArgumentException expected) { }

        Transaction transaction = new Transaction(9999, List.of(new BillItem(product, 2)));
        if (Math.abs(transaction.getTotal() - 20.0) > 0.001) throw new AssertionError("Transaction total failed");

        CreditCustomer customer = new CreditCustomer("Test Customer", "9999999999");
        customer.processTransaction(transaction);
        if (!customer.hasDue() || Math.abs(customer.getBalance() - 20.0) > 0.001) {
            throw new AssertionError("Credit balance failed");
        }
        customer.settleInMemory();
        if (customer.hasDue()) throw new AssertionError("Settlement failed");

        Path tempDir = Path.of("test_bills");
        FileReceiptWriter writer = new FileReceiptWriter(tempDir.toString());
        ReceiptTask task = new ReceiptTask(writer, transaction, "Test Customer");
        Thread thread = new Thread(task, "validation-receipt-thread");
        thread.start();
        thread.join();
        Path receipt = Path.of(task.getResult());
        if (!Files.exists(receipt)) throw new AssertionError("Receipt generation failed");
        Files.deleteIfExists(receipt);
        Files.deleteIfExists(tempDir);

        System.out.println("All validation and business-logic tests passed.");
    }
}
