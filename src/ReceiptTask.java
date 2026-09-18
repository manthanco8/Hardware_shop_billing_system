public class ReceiptTask implements Runnable {
    private final ReceiptWriter writer;
    private final Transaction transaction;
    private final String customerName;
    private String result;
    private Exception error;

    public ReceiptTask(ReceiptWriter writer, Transaction transaction, String customerName) {
        this.writer = writer;
        this.transaction = transaction;
        this.customerName = customerName;
    }

    @Override
    public void run() {
        try {
            result = writer.writeReceipt(transaction, customerName);
        } catch (Exception e) {
            error = e;
        }
    }

    public String getResult() throws Exception {
        if (error != null) throw error;
        return result;
    }
}
