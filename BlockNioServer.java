import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import nio.SelectorManager.ManagedSelector;
import util.AbstractLifeCycle;


public class BlockNioServer extends AbstractLifeCycle{
    private int port;
    private ServerSocketChannel acceptChannel;
    private SelectorManager selectorManager;
    private Executor executor;
    private int accepts;
    
    public BlockNioServer(int port) {
        this.port = port;
        accepts = Runtime.getRuntime().availableProcessors() /2;
        this.executor = Executors.newFixedThreadPool(50);
        selectorManager = new SelectorManager(executor, Runtime.getRuntime().availableProcessors());
    }
    
    private void open() throws IOException {
        acceptChannel = ServerSocketChannel.open();
        acceptChannel.socket().bind(new InetSocketAddress(port));
        acceptChannel.configureBlocking(true);
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        open();
        selectorManager.start();
        
        for (int i=1; i <= accepts; i ++) {
            executor.execute(new Acceptor(i));
        }
    }
    
    public void accept(int acceptorId) throws IOException {
        if (acceptChannel != null && acceptChannel.isOpen()) {
            SocketChannel socketChannel = acceptChannel.accept();
            socketChannel.configureBlocking(false);
            selectorManager.accept(socketChannel);
        }
    }
    
    private class Acceptor implements Runnable{
        private int acceptorId;
        public Acceptor(int id) {
            acceptorId = id;
        }
        public void run() {
            try {
                while (BlockNioServer.this.isRunning()) {
                    accept(acceptorId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        BlockNioServer server = new BlockNioServer(8080);
        server.start();
    }
}

class SelectorManager extends AbstractLifeCycle{
    private int selects;
    private ManagedSelector[] selectors;
    private long selectorIncrement;
    
    private Executor executor;
    
    public SelectorManager(Executor executor, int selects) {
        this.executor = executor;
        this.selects = selects;
        selectors = new ManagedSelector[selects];
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        for (int i=0; i < selects; i++) {
            ManagedSelector selector = new ManagedSelector(i);
            selectors[i] = selector;
            selector.start();
            executor.execute(selector);
        }
    }
    
    public void accept(SocketChannel channel) {
        ManagedSelector selector = chooseSelector();
        selector.submit(selector.new Acceptor(channel));
    }
    
    private ManagedSelector chooseSelector() {
        long s = selectorIncrement ++;
        int idx = (int) (s % selectors.length);
        return selectors[idx];
    }
    
    public class ManagedSelector extends AbstractLifeCycle implements Runnable{
        private int id;
        private Selector selector;
        private Thread currentThread;
        private boolean needsWakeUp = true;
        private boolean runningChanges = false;
        
        private ConcurrentLinkedQueue<Runnable> changes = new ConcurrentLinkedQueue<Runnable>();
        public ManagedSelector(int id) {
            this.id = id;
        }
        
        @Override
        protected void doStart() throws Exception {
            super.doStart();
            this.selector = Selector.open();
        }
        
        public void select() {
            try {
                processChanges();
                
                selector.select();
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                for (SelectionKey key : selectedKeys) {
                    if (key.isValid()) {
                        processKey(key);
                    }
                }

                selectedKeys.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
        
        private void wakeup() {
            selector.wakeup();
        }
        private void processKey(SelectionKey key) {
            Object obj = key.attachment();
            if (obj instanceof EndPoint) {
                ((EndPoint)obj).onSelected();
            }
        }
        
        public void submit(Runnable run) {
            if (Thread.currentThread() == currentThread) {
                if (runningChanges == true) {
                    changes.offer(run);
                } else {
                    runChanges();
                    runChange(run);
                }
            } else {
                changes.offer(run);
                if (needsWakeUp) {
                    wakeup();
                }
            }
        }
        

        private void processChanges() {
            runChanges();
            
            needsWakeUp = true;
            
            runChanges();
        }
        
        private void runChanges() {
            try {
                if (runningChanges) {
                    throw new IllegalStateException();
                }
                runningChanges = true;
                Runnable run;
                while ((run = changes.poll()) != null) {
                    runChange(run);
                }
            } finally {
                runningChanges = false;
            }
        }
        
        private void runChange(Runnable run) {
            run.run();
        }
        
        public void run() {
            this.currentThread = Thread.currentThread();
            while (SelectorManager.this.isRunning()) {
                select();
            }
        }
        
        private class Acceptor implements Runnable{
            private SocketChannel channel;
            public Acceptor(SocketChannel channel) {
                this.channel = channel;
            }
            
            public void run() {
                SelectionKey key;
                try {
                    key = channel.register(selector, 0, null);
                    EndPoint end = new EndPoint(key, channel, ManagedSelector.this, executor);
                    key.attach(end);
                    end.onOpened();
                } catch (ClosedChannelException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


class EndPoint {
    private SelectionKey key;
    private SocketChannel channel;
    private ManagedSelector managedSelector;
    private AtomicInteger interestOps = new AtomicInteger(0);
    private ByteBuffer toWrite = ByteBuffer.wrap("HTTP/1.1 200 OK\n\r\n\rHello".getBytes());
    private Executor executor;
    private Runnable readCallback = new Runnable() {
        
        public void run() {
            ByteBuffer readed = loclReaded.get();
            if (readed == null) {
                readed = ByteBuffer.allocateDirect(1024);
                loclReaded.set(readed);
            }
            try {
                channel.read(readed);
            } catch (IOException e) {
                e.printStackTrace();
            }
            writeCallback.run();
            ;//updateLocalInterests(SelectionKey.OP_WRITE, true);
        }
    };
    
    private Runnable openCallback = new Runnable() {
        
        public void run() {
            updateLocalInterests(SelectionKey.OP_READ, true);
        }
    };
    
    private Runnable writeCallback = new Runnable() {
        
        public void run() {
            try {
                channel.write(toWrite);
                close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private Runnable keyUpdateTask = new Runnable() {
        
        public void run() {
            if (channel.isOpen()) {
                int oldInterestOps = key.interestOps();
                int newInterestOps = interestOps.get();
                if (oldInterestOps != newInterestOps) {
                    key.interestOps(newInterestOps);
                }
            }
        }
        
    };
    
    private ThreadLocal<ByteBuffer> loclReaded = new ThreadLocal<ByteBuffer>();
    public EndPoint(SelectionKey key, SocketChannel channel, ManagedSelector managedSelector, Executor executor) {
        this.key = key;
        this.channel = channel;
        this.managedSelector = managedSelector;
        this.executor = executor;
    }
    
    public void onOpened() {
        managedSelector.submit(openCallback);
    }
    
    public void onSelected() {
        int oldInterestOps = key.interestOps();
        int readyOps = key.readyOps();
        int newInterestOps = oldInterestOps & ~readyOps;
        setKeyInterests(oldInterestOps, newInterestOps);
        updateLocalInterests(readyOps, false);
        
        if (key.isReadable()) {
            onReadable();
        }
        if (key.isWritable()) {
            onWriteable();
        }
    }
    
    private void onReadable() {
        executor.execute(readCallback);
    }
    
    private void onWriteable() {
        executor.execute(writeCallback);
    }
    
    public void close() {
        key.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void updateLocalInterests(int op, boolean add) {
        while (true) {
            int oldInterestOps = interestOps.get();
            int newInterestOps;
            if (add) {
                newInterestOps = oldInterestOps | op;
            } else {
                newInterestOps = oldInterestOps & ~op;
            }
            
            if (oldInterestOps != newInterestOps) {
                if (interestOps.compareAndSet(oldInterestOps, newInterestOps)) {
                    managedSelector.submit(keyUpdateTask);
                } else {
                    continue;
                }
            }
            break;
        }
    }
    
    private void setKeyInterests(int oldOps, int newOps) {
        key.interestOps(newOps);
    }
}
