public abstract class Customer {
    private final String name;
    private final String phone;

    protected Customer(String name, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be empty.");
        }
        this.name = name.trim();
        this.phone = phone == null ? null : phone.trim();
    }

    public String getName() { return name; }
    public String getPhone() { return phone; }

    public abstract void processTransaction(Transaction transaction);
}
