package auth;

public final class PhoneNumber {

    private PhoneNumber() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("[\\s().-]", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9]{10}")) {
            return null;
        }
        return normalized;
    }
}
