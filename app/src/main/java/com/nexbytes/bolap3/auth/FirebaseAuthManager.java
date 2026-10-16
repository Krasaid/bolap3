package com.nexbytes.bolap3.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class FirebaseAuthManager {

    private static final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface AuthCallback {
        void onReady(String uid);
        void onError(Exception exception);
    }

    public static void ensureUser(AuthCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            callback.onReady(currentUser.getUid());
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();

                    if (user != null) {
                        callback.onReady(user.getUid());
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public static String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}
