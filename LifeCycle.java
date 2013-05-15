public interface LifeCycle {
    void start() throws Exception;
    
    void stop() throws Exception;
    
    boolean isRunning();
    
    boolean isStarting();
    
    boolean isStarted();
    
    boolean isStopping();
    
    boolean isStopped();
    
    boolean isFailed();
}

