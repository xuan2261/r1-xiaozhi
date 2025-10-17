package com.phicomm.r1.xiaozhi.core;

/**
 * Listening modes từ py-xiaozhi
 * Định nghĩa các chế độ lắng nghe khác nhau
 */
public enum ListeningMode {
    /**
     * MANUAL: Push-to-talk mode
     * Người dùng giữ nút để nói, thả nút để dừng
     * Use case: Phát ngôn viên, presentation
     */
    MANUAL("manual"),
    
    /**
     * AUTO_STOP: Tự động dừng khi phát hiện silence
     * Phù hợp cho cuộc hội thoại thông thường
     * Use case: Normal conversation
     */
    AUTO_STOP("auto_stop"),
    
    /**
     * REALTIME: Continuous listening với AEC (Acoustic Echo Cancellation)
     * Luôn lắng nghe, thậm chí khi đang phát TTS
     * Yêu cầu: Hardware hỗ trợ AEC
     * Use case: Always-on voice assistant
     */
    REALTIME("realtime");
    
    private final String value;
    
    ListeningMode(String value) {
        this.value = value;
    }
    
    /**
     * Get giá trị string của mode
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Convert từ string sang ListeningMode
     */
    public static ListeningMode fromString(String value) {
        if (value == null) {
            return AUTO_STOP;
        }
        
        for (ListeningMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return AUTO_STOP;
    }
    
    @Override
    public String toString() {
        return value;
    }
}