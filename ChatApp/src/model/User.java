package model;

public class User {

    private String username;
    private String phoneNumber;
    private String password;
    private String status; // ONLINE | OFFLINE

    public User() {
    }

    public User(String username, String password, String status) {
        this(username, null, password, status);
    }

    public User(String username, String phoneNumber, String password, String status) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getIdentity() {
        return phoneNumber == null || phoneNumber.trim().isEmpty()
                ? username : phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
