package com.phicomm.r1.xiaozhi.core;

/**
 * Device states từ py-xiaozhi
 * Định nghĩa 3 trạng thái chính của thiết bị
 */
public enum DeviceState {
    /**
     * Trạng thái chờ - không hoạt động
     */
    IDLE("idle"),
    
    /**
     * Đang lắng nghe - thu âm và nhận diện giọng nói
     */
    LISTENING("listening"),
    
    /**
     * Đang nói - phát audio TTS
     */
    SPEAKING("speaking");
    
    private final String value;
    
    DeviceState(String value) {
        this.value = value;
    }
    
    /**
     * Get giá trị string của state
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Convert từ string sang DeviceState
     */
    public static DeviceState fromString(String value) {
        if (value == null) {
            return IDLE;
        }
        
        for (DeviceState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return IDLE;
    }
    
    @Override
    public String toString() {
        return value;
    }
}