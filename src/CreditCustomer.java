import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreditCustomer extends Customer {
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    private double balance;

    public CreditCustomer(String name, String phone) {
        super(name, validatePhone(phone));
    }

    private static String validatePhone(String phone) {
        if (phone == null || !phone.matches("[0-9]{10,15}")) {
            throw new IllegalArgumentException("Phone number must contain 10 to 15 digits.");
        }
        return phone;
    }

    public void addPending(Transaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("Transaction cannot be null.");
        pendingTransactions.add(transaction);
        balance += transaction.getTotal();
    }

    public double getBalance() { return balance; }
    public boolean hasDue() { return balance > 0.005; }
    public List<Transaction> getPendingTransactions() { return Collections.unmodifiableList(pendingTransactions); }

    public void setBalanceFromDatabase(double balance) {
        if (balance < 0) throw new IllegalArgumentException("Balance cannot be negative.");
        this.balance = balance;
    }

    public void settleInMemory() throws NoOutstandingBalanceException {
        if (!hasDue()) throw new NoOutstandingBalanceException(getName() + " has no outstanding balance.");
        pendingTransactions.clear();
        balance = 0;
    }

    @Override
    public void processTransaction(Transaction transaction) {
        addPending(transaction);
        System.out.printf("Added to %s's khata. Balance: Rs.%.2f%n", getName(), balance);
    }

    public String settle() throws NoOutstandingBalanceException {
        double amount = balance;
        settleInMemory();
        return String.format("Account settled for %s. Amount paid: Rs.%.2f", getName(), amount);
    }
}
