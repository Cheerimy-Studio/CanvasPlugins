package com.fabian.xclearlag.api;

/**
 * Categorizes the reason for a cleanup event.
 */
public enum CleanupReason {
    /** Triggered automatically by the scheduler. */
    SCHEDULE_TRIGGERED,
    
    /** Triggered automatically due to low server TPS. */
    TPS_TRIGGERED,
    
    /** Triggered manually by a player or console command. */
    MANUAL_TRIGGERED,
    
    /** Triggered by an external plugin using the API. */
    API_TRIGGERED,
    
    /** Unknown or unspecified reason. */
    UNKNOWN
}
