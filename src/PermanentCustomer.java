import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PermanentCustomer extends Customer {

    private List<Transaction> pendingTransactions = new ArrayList<>(); // Collections framework
    private double outstandingBalance = 0.0;

    public PermanentCustomer(String name, String phoneNumber) {
        super(name, phoneNumber);
    }

    // Used by DatabaseManager to rebuild a customer that already has history
    // (unsettled purchases + balance) when the program restarts.
    public PermanentCustomer(String name, String phoneNumber, double existingBalance,
                              List<Transaction> existingPendingTransactions) {
        super(name, phoneNumber);
        this.outstandingBalance = existingBalance;
        this.pendingTransactions = existingPendingTransactions;
    }

    public double getOutstandingBalance() {
        return outstandingBalance;
    }

    @Override
    public void recordPurchase(Transaction transaction) {
        // No payment taken now - just add to the running khata
        pendingTransactions.add(transaction);
        outstandingBalance += transaction.getTotal();

        // Persist to the database (Unit 5: JDBC) so the khata survives a restart
        DatabaseManager.upsertCustomer(phoneNumber, name, outstandingBalance);
        String txnDate = String.valueOf(transaction.getDate().getTime());
        int txnId = DatabaseManager.insertTransaction(phoneNumber, name, "PERMANENT",
                txnDate, transaction.getTotal(), false);
        if (txnId != -1) {
            transaction.setId(txnId);
            for (BillItem item : transaction.getItems()) {
                DatabaseManager.insertTransactionItem(txnId, item.getProduct().getId(),
                        item.getProduct().getName(), item.getQuantity(),
                        item.getProduct().getPrice(), item.getSubtotal());
            }
        }

        String message = "Hi " + name + ", added to your account:\n"
                + transaction.toReceiptLine()
                + "Running balance: Rs." + String.format("%.2f", outstandingBalance);

        // Fire the WhatsApp notification on its own thread so the counter
        // can move to the next customer immediately -> multithreading
        new Thread(new WhatsAppSender(phoneNumber, message)).start();

        System.out.println("Added to " + name + "'s khata. Notifying on WhatsApp...");
    }

    public String settleAccount() throws NoOutstandingDueException {
        if (pendingTransactions.isEmpty()) {
            throw new NoOutstandingDueException(name + " has no pending dues.");
        }

        StringBuilder statement = new StringBuilder();
        statement.append("===== FINAL SETTLEMENT: ").append(name).append(" =====\n");
        for (Transaction t : pendingTransactions) {
            statement.append(t.toReceiptLine());
        }
        statement.append("--------------------------------\n");
        statement.append(String.format("TOTAL PAID: Rs.%.2f%n", outstandingBalance));
        statement.append("Account cleared. Thank you!\n");

        String finalMessage = statement.toString();
        new Thread(new WhatsAppSender(phoneNumber, finalMessage)).start();

        // Persist the settlement as a permanent audit-trail record, and mark the
        // underlying purchases settled instead of deleting them (Unit 5: JDBC +
        // the "keep an audit trail" roadmap item from the project doc).
        DatabaseManager.markTransactionsSettled(phoneNumber);
        DatabaseManager.insertSettlement(phoneNumber, name,
                String.valueOf(new Date().getTime()), outstandingBalance);
        DatabaseManager.upsertCustomer(phoneNumber, name, 0.0);

        pendingTransactions.clear();
        outstandingBalance = 0.0;

        return finalMessage;
    }
}
