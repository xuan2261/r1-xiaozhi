package com.phicomm.r1.xiaozhi.events;

import com.phicomm.r1.xiaozhi.core.DeviceState;

/**
 * Event được broadcast khi device state thay đổi
 * Theo mô hình py-xiaozhi event system
 */
public class StateChangedEvent {
    public final DeviceState oldState;
    public final DeviceState newState;
    public final long timestamp;
    
    public StateChangedEvent(DeviceState oldState, DeviceState newState) {
        this.oldState = oldState;
        this.newState = newState;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "StateChangedEvent{" +
                "oldState=" + oldState +
                ", newState=" + newState +
                ", timestamp=" + timestamp +
                '}';
    }
}