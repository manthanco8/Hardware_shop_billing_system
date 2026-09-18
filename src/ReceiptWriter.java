import java.io.IOException;

public interface ReceiptWriter {
    String writeReceipt(Transaction transaction, String customerName) throws IOException;
}
