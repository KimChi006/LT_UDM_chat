package auth;

import model.User;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class UserSession {

    private static final UserSession INSTANCE = new UserSession();

    private final Set<UserSessionListener> listeners =
            Collections.synchronizedSet(new LinkedHashSet<UserSessionListener>());
    private User currentUser;

    private UserSession() {
    }

    public static UserSession getInstance() {
        return INSTANCE;
    }

    public synchronized void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        currentUser = user;
        notifyListeners();
    }

    public synchronized void logout() {
        if (currentUser != null) {
            currentUser = null;
            notifyListeners();
        }
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized String getUsername() {
        return currentUser == null ? null : currentUser.getUsername();
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public void addListener(UserSessionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(UserSessionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        Set<UserSessionListener> snapshot;
        synchronized (listeners) {
            snapshot = new LinkedHashSet<UserSessionListener>(listeners);
        }
        for (UserSessionListener listener : snapshot) {
            listener.onSessionChanged(currentUser);
        }
    }

    public interface UserSessionListener {
        void onSessionChanged(User currentUser);
    }
}