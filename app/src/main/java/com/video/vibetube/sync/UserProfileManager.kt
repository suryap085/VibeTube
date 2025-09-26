package com.video.vibetube.sync

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * User Profile Manager for handling profile images and user information
 * Integrates with Firebase Authentication and Storage
 */
class UserProfileManager private constructor(private val context: Context) {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    
    companion object {
        private const val TAG = "UserProfileManager"
        private const val COLLECTION_USER_PROFILES = "user_profiles"
        private const val STORAGE_PROFILE_IMAGES = "profile_images"
        
        @Volatile
        private var INSTANCE: UserProfileManager? = null
        
        fun getInstance(context: Context): UserProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    data class UserProfile(
        val uid: String = "",
        val displayName: String = "",
        val email: String = "",
        val photoUrl: String? = null,
        val customPhotoUrl: String? = null,
        val syncEnabled: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    /**
     * Get current user profile information
     */
    fun getCurrentUserProfile(): UserProfile? {
        val user = auth.currentUser ?: return null
        
        return UserProfile(
            uid = user.uid,
            displayName = user.displayName ?: "VibeTube User",
            email = user.email ?: "",
            photoUrl = user.photoUrl?.toString(),
            syncEnabled = true
        )
    }
    
    /**
     * Get user profile from Firestore
     */
    suspend fun getUserProfile(): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val document = firestore.collection(COLLECTION_USER_PROFILES)
                .document(user.uid)
                .get()
                .await()
            
            if (document.exists()) {
                val profile = document.toObject(UserProfile::class.java)
                    ?: return Result.failure(Exception("Failed to parse user profile"))
                
                Log.d(TAG, "User profile retrieved: ${profile.displayName}")
                Result.success(profile)
            } else {
                // Create new profile from Firebase Auth data
                val newProfile = getCurrentUserProfile()
                    ?: return Result.failure(Exception("Failed to create user profile"))
                
                saveUserProfile(newProfile)
                Result.success(newProfile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save user profile to Firestore
     */
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val updatedProfile = profile.copy(
                uid = user.uid,
                lastUpdated = System.currentTimeMillis()
            )
            
            firestore.collection(COLLECTION_USER_PROFILES)
                .document(user.uid)
                .set(updatedProfile)
                .await()
            
            Log.d(TAG, "User profile saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload profile image to Firebase Storage
     */
    suspend fun uploadProfileImage(imageUri: Uri): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val imageRef: StorageReference = storage.reference
                .child(STORAGE_PROFILE_IMAGES)
                .child("${user.uid}.jpg")
            
            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            Log.d(TAG, "Profile image uploaded successfully")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload profile image from bitmap
     */
    suspend fun uploadProfileImage(bitmap: Bitmap): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()
            
            val imageRef: StorageReference = storage.reference
                .child(STORAGE_PROFILE_IMAGES)
                .child("${user.uid}.jpg")
            
            val uploadTask = imageRef.putBytes(data).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            
            Log.d(TAG, "Profile image uploaded successfully from bitmap")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload profile image from bitmap", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete profile image from Firebase Storage
     */
    suspend fun deleteProfileImage(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val imageRef: StorageReference = storage.reference
                .child(STORAGE_PROFILE_IMAGES)
                .child("${user.uid}.jpg")
            
            imageRef.delete().await()
            
            Log.d(TAG, "Profile image deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete profile image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update user profile with new photo URL
     */
    suspend fun updateProfilePhoto(photoUrl: String): Result<Unit> {
        return try {
            val currentProfile = getUserProfile().getOrNull()
                ?: return Result.failure(Exception("Failed to get current profile"))
            
            val updatedProfile = currentProfile.copy(
                customPhotoUrl = photoUrl,
                lastUpdated = System.currentTimeMillis()
            )
            
            saveUserProfile(updatedProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update profile photo", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the best available profile photo URL
     */
    suspend fun getProfilePhotoUrl(): String? {
        return try {
            val profile = getUserProfile().getOrNull()
            profile?.customPhotoUrl ?: profile?.photoUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profile photo URL", e)
            auth.currentUser?.photoUrl?.toString()
        }
    }
    
    /**
     * Update display name
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        return try {
            val currentProfile = getUserProfile().getOrNull()
                ?: return Result.failure(Exception("Failed to get current profile"))
            
            val updatedProfile = currentProfile.copy(
                displayName = displayName,
                lastUpdated = System.currentTimeMillis()
            )
            
            saveUserProfile(updatedProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update display name", e)
            Result.failure(e)
        }
    }
    
    /**
     * Enable or disable sync for user
     */
    suspend fun setSyncEnabled(enabled: Boolean): Result<Unit> {
        return try {
            val currentProfile = getUserProfile().getOrNull()
                ?: return Result.failure(Exception("Failed to get current profile"))
            
            val updatedProfile = currentProfile.copy(
                syncEnabled = enabled,
                lastUpdated = System.currentTimeMillis()
            )
            
            saveUserProfile(updatedProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sync setting", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete user profile completely
     */
    suspend fun deleteUserProfile(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            // Delete profile image
            deleteProfileImage()
            
            // Delete profile document
            firestore.collection(COLLECTION_USER_PROFILES)
                .document(user.uid)
                .delete()
                .await()
            
            Log.d(TAG, "User profile deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user profile", e)
            Result.failure(e)
        }
    }
}
