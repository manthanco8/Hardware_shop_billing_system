public abstract class Customer {

    protected String name;
    protected String phoneNumber;

    public Customer(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }

    // Each customer type reacts to a new purchase differently -> polymorphism
    public abstract void recordPurchase(Transaction transaction);
}
