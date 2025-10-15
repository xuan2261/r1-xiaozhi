package com.phicomm.r1.xiaozhi.api.model;

/**
 * Response từ API check device status
 */
public class DeviceStatus {
    private String status; // "pending", "paired", "expired"
    private String token;
    private String deviceId;
    
    public DeviceStatus(String status, String token, String deviceId) {
        this.status = status;
        this.token = token;
        this.deviceId = deviceId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getToken() {
        return token;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public boolean isPaired() {
        return "paired".equals(status);
    }
    
    public boolean isPending() {
        return "pending".equals(status);
    }
    
    public boolean isExpired() {
        return "expired".equals(status);
    }
}