package com.example.aryanonkar;

import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseUtils {
    private static FirebaseFirestore firestoreInstance;

    public static FirebaseFirestore getFirestore() {
        if (firestoreInstance == null) {
            firestoreInstance = FirebaseFirestore.getInstance();
        }
        return firestoreInstance;
    }
}
