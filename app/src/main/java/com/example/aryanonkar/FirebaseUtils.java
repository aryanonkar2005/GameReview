package com.example.aryanonkar;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseUtils {
    private static FirebaseFirestore firestoreInstance;
    private static FirebaseDatabase firebaseDbInstance;

    public static FirebaseFirestore getFirestore() {
        if (firestoreInstance == null) {
            firestoreInstance = FirebaseFirestore.getInstance();
        }
        return firestoreInstance;
    }

    public static FirebaseDatabase getFirebaseDb() {
        if (firebaseDbInstance == null) {
            firebaseDbInstance = FirebaseDatabase.getInstance();
        }
        return firebaseDbInstance;
    }
}
