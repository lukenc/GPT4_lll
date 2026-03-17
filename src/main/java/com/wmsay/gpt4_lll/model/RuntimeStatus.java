package com.wmsay.gpt4_lll.model;

public enum RuntimeStatus {
    IDLE,       // No active request
    RUNNING,    // AI request in progress
    COMPLETED,  // Last request finished successfully
    ERROR       // Last request failed or was cancelled
}
