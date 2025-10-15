package com.phicomm.r1.xiaozhi.util;

/**
 * Xiaozhi Error Codes và Vietnamese messages
 * Học từ py-xiaozhi và android-client implementations
 */
public class ErrorCodes {
    
    // Pairing & Authentication Errors (1xxx)
    public static final int INVALID_PAIRING_CODE = 1001;
    public static final int DEVICE_NOT_REGISTERED = 1002;
    public static final int AUTHORIZATION_FAILED = 1003;
    public static final int TOKEN_EXPIRED = 1004;
    
    // Network Errors (2xxx)
    public static final int NETWORK_ERROR = 2001;
    public static final int CONNECTION_TIMEOUT = 2002;
    public static final int WEBSOCKET_ERROR = 2003;
    public static final int SERVER_UNAVAILABLE = 2004;
    
    // Audio Processing Errors (3xxx)
    public static final int AUDIO_PROCESSING_ERROR = 3001;
    public static final int TTS_GENERATION_FAILED = 3002;
    public static final int VOICE_RECOGNITION_FAILED = 3003;
    public static final int AUDIO_PLAYBACK_ERROR = 3004;
    
    // Agent Errors (4xxx)
    public static final int AGENT_NOT_FOUND = 4001;
    public static final int AGENT_UNAVAILABLE = 4002;
    public static final int AGENT_ERROR = 4003;
    
    // General Errors (9xxx)
    public static final int UNKNOWN_ERROR = 9000;
    public static final int INVALID_MESSAGE_FORMAT = 9001;
    public static final int RATE_LIMIT_EXCEEDED = 9002;
    
    /**
     * Get Vietnamese error message for error code
     */
    public static String getMessage(int code) {
        switch (code) {
            // Pairing & Auth
            case INVALID_PAIRING_CODE:
                return "Mã ghép nối không hợp lệ. Vui lòng kiểm tra lại.";
            case DEVICE_NOT_REGISTERED:
                return "Thiết bị chưa được đăng ký. Vui lòng ghép nối lại.";
            case AUTHORIZATION_FAILED:
                return "Xác thực thất bại. Vui lòng thử lại.";
            case TOKEN_EXPIRED:
                return "Phiên đăng nhập đã hết hạn. Vui lòng ghép nối lại.";
            
            // Network
            case NETWORK_ERROR:
                return "Lỗi mạng. Vui lòng kiểm tra kết nối internet.";
            case CONNECTION_TIMEOUT:
                return "Kết nối bị timeout. Vui lòng thử lại.";
            case WEBSOCKET_ERROR:
                return "Lỗi WebSocket. Đang thử kết nối lại...";
            case SERVER_UNAVAILABLE:
                return "Server không khả dụng. Vui lòng thử lại sau.";
            
            // Audio
            case AUDIO_PROCESSING_ERROR:
                return "Lỗi xử lý âm thanh. Vui lòng thử lại.";
            case TTS_GENERATION_FAILED:
                return "Lỗi tạo giọng nói. Vui lòng thử lại.";
            case VOICE_RECOGNITION_FAILED:
                return "Không nhận diện được giọng nói. Vui lòng nói rõ hơn.";
            case AUDIO_PLAYBACK_ERROR:
                return "Lỗi phát âm thanh. Vui lòng kiểm tra loa.";
            
            // Agent
            case AGENT_NOT_FOUND:
                return "Không tìm thấy agent. Vui lòng chọn agent khác.";
            case AGENT_UNAVAILABLE:
                return "Agent không khả dụng. Vui lòng thử lại sau.";
            case AGENT_ERROR:
                return "Lỗi agent. Vui lòng liên hệ hỗ trợ.";
            
            // General
            case RATE_LIMIT_EXCEEDED:
                return "Quá nhiều request. Vui lòng đợi một chút.";
            case INVALID_MESSAGE_FORMAT:
                return "Định dạng tin nhắn không hợp lệ.";
            case UNKNOWN_ERROR:
            default:
                return "Lỗi không xác định (Code: " + code + ")";
        }
    }
    
    /**
     * Get English error message (for logging)
     */
    public static String getEnglishMessage(int code) {
        switch (code) {
            case INVALID_PAIRING_CODE: return "Invalid pairing code";
            case DEVICE_NOT_REGISTERED: return "Device not registered";
            case AUTHORIZATION_FAILED: return "Authorization failed";
            case TOKEN_EXPIRED: return "Token expired";
            case NETWORK_ERROR: return "Network error";
            case CONNECTION_TIMEOUT: return "Connection timeout";
            case WEBSOCKET_ERROR: return "WebSocket error";
            case SERVER_UNAVAILABLE: return "Server unavailable";
            case AUDIO_PROCESSING_ERROR: return "Audio processing error";
            case TTS_GENERATION_FAILED: return "TTS generation failed";
            case VOICE_RECOGNITION_FAILED: return "Voice recognition failed";
            case AUDIO_PLAYBACK_ERROR: return "Audio playback error";
            case AGENT_NOT_FOUND: return "Agent not found";
            case AGENT_UNAVAILABLE: return "Agent unavailable";
            case AGENT_ERROR: return "Agent error";
            case RATE_LIMIT_EXCEEDED: return "Rate limit exceeded";
            case INVALID_MESSAGE_FORMAT: return "Invalid message format";
            default: return "Unknown error";
        }
    }
    
    /**
     * Check if error is retryable
     */
    public static boolean isRetryable(int code) {
        switch (code) {
            case NETWORK_ERROR:
            case CONNECTION_TIMEOUT:
            case WEBSOCKET_ERROR:
            case SERVER_UNAVAILABLE:
            case AGENT_UNAVAILABLE:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get recommended retry delay in milliseconds
     */
    public static int getRetryDelay(int code, int retryCount) {
        // Exponential backoff: 1s, 2s, 4s, 8s, ...
        int baseDelay = 1000;
        
        if (code == RATE_LIMIT_EXCEEDED) {
            // Longer delay for rate limiting
            baseDelay = 5000;
        }
        
        return baseDelay * (int) Math.pow(2, Math.min(retryCount, 4));
    }
    
    /**
     * Parse error code from server response
     * Response format: {"payload": {"code": "1001", "message": "..."}}
     */
    public static int parseErrorCode(String codeString) {
        try {
            return Integer.parseInt(codeString);
        } catch (NumberFormatException e) {
            return UNKNOWN_ERROR;
        }
    }
}