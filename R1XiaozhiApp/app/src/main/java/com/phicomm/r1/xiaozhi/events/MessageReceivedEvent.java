package com.phicomm.r1.xiaozhi.events;

import org.json.JSONObject;

/**
 * Event được broadcast khi nhận được JSON message từ server
 */
public class MessageReceivedEvent {
    public final JSONObject message;
    public final String messageType;
    public final long timestamp;
    
    public MessageReceivedEvent(JSONObject message) {
        this.message = message;
        this.messageType = extractMessageType(message);
        this.timestamp = System.currentTimeMillis();
    }
    
    private String extractMessageType(JSONObject message) {
        try {
            if (message.has("header")) {
                JSONObject header = message.getJSONObject("header");
                return header.optString("name", "unknown");
            }
            return message.optString("type", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    @Override
    public String toString() {
        return "MessageReceivedEvent{" +
                "messageType='" + messageType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}