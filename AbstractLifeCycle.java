public abstract class AbstractLifeCycle implements LifeCycle{
    public static final String STOPPED="STOPPED";
    public static final String FAILED="FAILED";
    public static final String STARTING="STARTING";
    public static final String STARTED="STARTED";
    public static final String STOPPING="STOPPING";
    public static final String RUNNING="RUNNING";
    
    private final int __FAILED = -1,__STOPPED = 0 ,__STARTING=1,__STARTED=2,__STOPPING=3;
    private volatile int _state = __STOPPED;
    private final Object _lock = new Object();
    
    protected void doStart() throws Exception{
        
    }
    
    protected void doStop() throws Exception{
        
    }
    
    public final void start() throws Exception{
        synchronized (_lock) {
            try{
                if(_state == __STARTED || _state == __STARTING)
                    return;
                setStarting();
                doStart();
                setStarted();
            }catch (Exception e) {
                setFailed();
                throw e;
            }catch (Error e) {
                setFailed();
                throw e;
            }
            
        }
    }
    
    public final void stop() throws Exception{
        synchronized (_lock) {
            try{
                if(_state == __STOPPED || _state == __STOPPING)
                    return;
                setStopping();
                doStop();
                setStopped();
            }catch (Exception e) {
                setFailed();
                throw e;
            }catch (Error e) {
                setFailed();
                throw e;
            }
            
        }
    }

    public boolean isRunning()
    {
        final int state = _state;
        
        return state == __STARTED || state == __STARTING;
    }

    public boolean isStarted()
    {
        return _state == __STARTED;
    }

    public boolean isStarting()
    {
        return _state == __STARTING;
    }

    public boolean isStopping()
    {
        return _state == __STOPPING;
    }

    public boolean isStopped()
    {
        return _state == __STOPPED;
    }

    public boolean isFailed()
    {
        return _state == __FAILED;
    }
    
    private void setStopped() {
        _state = __STOPPED;
    }

    private void setStopping() {
        _state = __STOPPING;
    }

    private void setFailed() {
       _state = __FAILED;
    }

    private void setStarted() {
        _state = __STARTED;
    }

    private void setStarting() {
        _state = __STARTING;
    }
}

