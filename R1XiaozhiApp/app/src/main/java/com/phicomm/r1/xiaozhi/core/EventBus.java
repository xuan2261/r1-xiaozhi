package com.phicomm.r1.xiaozhi.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event broadcasting system theo mô hình py-xiaozhi
 * Thread-safe và post events trên main thread
 * 
 * Usage:
 * // Register listener
 * eventBus.register(StateChangedEvent.class, event -> {
 *     // Handle event on main thread
 * });
 * 
 * // Post event
 * eventBus.post(new StateChangedEvent(oldState, newState));
 */
public class EventBus {
    
    private static final String TAG = "EventBus";
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    
    /**
     * Register một listener cho event type cụ thể
     * Thread-safe: có thể gọi từ bất kỳ thread nào
     * 
     * @param eventType Class của event (vd: StateChangedEvent.class)
     * @param listener Listener sẽ được gọi khi event được post
     */
    public <T> void register(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners == null) {
            eventListeners = new CopyOnWriteArrayList<>();
            listeners.put(eventType, eventListeners);
        }
        eventListeners.add(listener);
        Log.d(TAG, "Registered listener for " + eventType.getSimpleName() + 
              " (total: " + eventListeners.size() + ")");
    }
    
    /**
     * Unregister một listener
     * Thread-safe: có thể gọi từ bất kỳ thread nào
     * 
     * @param eventType Class của event
     * @param listener Listener cần unregister
     */
    public <T> void unregister(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            Log.d(TAG, "Unregistered listener for " + eventType.getSimpleName() +
                  " (remaining: " + eventListeners.size() + ")");
        }
    }
    
    /**
     * Post event tới tất cả listeners (trên main thread)
     * Thread-safe: có thể gọi từ bất kỳ thread nào
     * 
     * @param event Event object cần broadcast
     */
    public <T> void post(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            Log.d(TAG, "No listeners for " + eventType.getSimpleName());
            return;
        }
        
        Log.d(TAG, "Broadcasting " + eventType.getSimpleName() + " to " + 
              eventListeners.size() + " listeners");
        
        // Post tất cả listeners trên main thread
        for (final EventListener listener : eventListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in event listener for " + 
                              event.getClass().getSimpleName(), e);
                    }
                }
            });
        }
    }
    
    /**
     * Post event ngay lập tức (trên current thread)
     * CẢNH BÁO: Chỉ dùng nếu chắc chắn đang ở main thread
     * 
     * @param event Event object cần broadcast
     */
    public <T> void postSync(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Broadcasting (sync) " + eventType.getSimpleName() + " to " + 
              eventListeners.size() + " listeners");
        
        for (final EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in event listener for " + 
                      event.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * Clear tất cả listeners
     * Thường được gọi khi shutdown app
     */
    public void clear() {
        int totalListeners = 0;
        for (List<EventListener<?>> list : listeners.values()) {
            totalListeners += list.size();
        }
        listeners.clear();
        Log.d(TAG, "Cleared all listeners (total was: " + totalListeners + ")");
    }
    
    /**
     * Get số lượng listeners cho một event type
     */
    public <T> int getListenerCount(Class<T> eventType) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        return eventListeners != null ? eventListeners.size() : 0;
    }
    
    /**
     * Event listener interface
     * 
     * @param <T> Event type
     */
    public interface EventListener<T> {
        /**
         * Được gọi khi event được post
         * LUÔN được gọi trên main thread (UI thread)
         * 
         * @param event Event object
         */
        void onEvent(T event);
    }
}