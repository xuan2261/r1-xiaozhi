package com.phicomm.r1.xiaozhi.api.model;

/**
 * Response từ API register device
 */
public class PairingResponse {
    private String code;
    private String deviceId;
    private long expiresAt;
    
    public PairingResponse(String code, String deviceId, long expiresAt) {
        this.code = code;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}