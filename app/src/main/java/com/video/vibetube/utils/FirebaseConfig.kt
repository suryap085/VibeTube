package com.video.vibetube.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class FirebaseConfig {
    companion object {
        fun initializeFirestore() {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()

            FirebaseFirestore.getInstance().firestoreSettings = settings
        }
    }
}