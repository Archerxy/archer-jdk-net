package com.archer.jdknet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class ClientChannel {

	private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
	protected static final int CORE_THREAD_SIZE = PROCESSORS > 1 ? 2 : PROCESSORS;
	protected static final int MAX_THREAD_SIZE = PROCESSORS << 2;

	private static ClientWorkerThread workerThread;
	private static Selector selector;
	private static volatile boolean running = false;
    private static ConcurrentHashMap<SelectionKey, ClientChannel> 
    						channelCache = new ConcurrentHashMap<>();
	
	private String host;
	private int port;
	
	private HandlerWorker worker;
	private Channel channel;
	private SelectionKey key;
	
	private volatile boolean connected = false;
	
	public ClientChannel(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
	}
	
	public ClientChannel initHandlerWorker() {
		initHandlerWorker(CORE_THREAD_SIZE, MAX_THREAD_SIZE);
		return this;
	}
	
	public ClientChannel initHandlerWorker(int coreThreads, int maxThreads) {
		this.worker = new HandlerWorker(coreThreads, maxThreads);
		return this;
	}
	
	public ClientChannel initHandlerWorker(HandlerWorker worker) {
		this.worker = worker;
		return this;
	}
	
	public void connect() throws IOException {
		if(connected) {
			return ;
		}
		if(worker == null) {
			initHandlerWorker();
		}
		if(!worker.isAlive()) {
			worker.reStart();
		}
		if(selector == null || !selector.isOpen()) {
			selector = Selector.open();
		}
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
		channel = new Channel(host, port, key, worker);
		channel.clientMode(true);
		channel.clientChannel(this);
		key.attach(channel);
		channelCache.put(key, this);
		
		socketChannel.connect(new InetSocketAddress(host, port));
		if(!running) {
			running = true;
			workerThread = new ClientWorkerThread();
			workerThread.start();
		}
	}
	
	public void send(Bytes out) throws Exception {
		worker.handleWrite(channel, out);
	}

    public void close() {
    	connected = false;
    	worker.close();
    	channelCache.remove(key);
    	key.cancel();
    	if(channelCache.size() <= 0) {
    		shutdown();
    	}
    }
    
    public boolean isAlive() {
    	return channel.isOpen();
    }

    public ClientChannel add(Handler ...handlers) {
		if(worker == null) {
			initHandlerWorker();
		}
    	worker.add(handlers);
    	return this;
    }
    
    public ClientChannel push(Handler handler) {
		if(worker == null) {
			initHandlerWorker();
		}
    	worker.push(handler);
    	return this;
    }
    
    public ClientChannel shift(Handler handler) {
		if(worker == null) {
			initHandlerWorker();
		}
    	worker.shift(handler);
    	return this;
    }
    
    public String host() {
    	return host;
    }
    
    public int port() {
    	return port;
    }
    
    public String endpoint() {
    	return host+":"+port;
    }
    
    public boolean handlerInitialized() {
    	return worker.handlerInitialized();
    }
    
    private static void shutdown() {
    	running = false;
    	selector.wakeup();
    }
    
    private void handle(SelectionKey sk) throws IOException {
    	SocketChannel sc = (SocketChannel) sk.channel();
    	try {
        	if(sk.isConnectable() && sc.finishConnect()) {
        		connected = true;
    			worker.onConnect(sk);
    		} 
    		if (sk.isReadable()) {
    			worker.onRead(sk);
            }
    		if (sk.isWritable()) {
    			worker.onWrite(sk);
            }
    	} catch(CancelledKeyException ignore) {
    		worker.onClose(sk);
    	}
    }
    
    private class ClientWorkerThread extends Thread {
    	
    	@Override
    	public void run() {
            try {
            	running = true;
                while (running) {
                	
                    if(selector.select() <= 0) {
                    	continue;
                    }
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        it.remove();
                        if(sk.isValid()) {
                        	ClientChannel clientChannel = channelCache.getOrDefault(sk, null);
                        	if(clientChannel != null) {
                        		clientChannel.handle(sk);
                        	} else {
                        		sk.cancel();
                        	}
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    selector.close();
                } catch(Exception ignore) {}
                worker.close();
            }
    	}
    }
}
