public class WalkInCustomer extends Customer {
    public WalkInCustomer(String name) { super(name, null); }

    @Override
    public void processTransaction(Transaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("Transaction cannot be null.");
        System.out.println("Walk-in sale recorded for " + getName() + ".");
    }
}
