package com.example.irondiary.data.local

/**
 * Represents the synchronization status of a particular entity
 * between the local Room database and remote Firestore.
 */
enum class SyncState {
    /** Change has been successfully synced with Firestore. */
    SYNCED,
    
    /** Local change requires uploading to Firestore. */
    PENDING,
    
    /** The last sync attempt failed, usually due to network failure. Should be retried. */
    FAILED,
    
    /** Item is soft-deleted locally and waiting for deletion on Firestore. */
    DELETED
}
