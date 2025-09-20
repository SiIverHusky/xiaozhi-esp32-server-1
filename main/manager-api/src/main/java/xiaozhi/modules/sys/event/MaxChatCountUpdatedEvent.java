package xiaozhi.modules.sys.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when MAX_CHAT_COUNT parameter is updated
 */
public class MaxChatCountUpdatedEvent extends ApplicationEvent {
    
    private final Integer oldValue;
    private final Integer newValue;
    
    public MaxChatCountUpdatedEvent(Object source, Integer oldValue, Integer newValue) {
        super(source);
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    public Integer getOldValue() {
        return oldValue;
    }
    
    public Integer getNewValue() {
        return newValue;
    }
}
