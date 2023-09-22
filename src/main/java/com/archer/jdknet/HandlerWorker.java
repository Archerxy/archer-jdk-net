package com.archer.jdknet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class HandlerWorker {
	
	private static final int WORKER_POOL_CORE_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int WORKER_POOL_MAX_SIZE = WORKER_POOL_CORE_SIZE * 2;
    private static final int KEEP_ALIVE_TIME = 10_000;
    private static ThreadPoolExecutor workerPool;
    private static AtomicInteger workerCount = new AtomicInteger(0);


	protected static final HandlerWorker INSTANCE = new HandlerWorker(
			ClientChannel.CORE_THREAD_SIZE, ClientChannel.MAX_THREAD_SIZE
			);
	
    private Handler head;
    private Handler tail;
    
    private int coreThreads, maxThreads;
    
    public HandlerWorker() {
    	this(WORKER_POOL_CORE_SIZE, WORKER_POOL_MAX_SIZE);
    }
    
    public HandlerWorker(int coreThreads, int maxThreads) {
    	this.coreThreads = coreThreads;
    	this.maxThreads = maxThreads;
    	if(workerPool == null) {
    		workerPool = new ThreadPoolExecutor(
            		coreThreads,
            		maxThreads,
            		KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    Executors.defaultThreadFactory());
    	}
    	workerCount.incrementAndGet();
    }
    
    protected void onAccept(SelectionKey serverKey) {
    	SelectionKey clientKey = null;
    	try {
    		ServerSocketChannel serverChannel = (ServerSocketChannel) serverKey.channel();
        	SocketChannel clientChannel = serverChannel.accept();
        	clientChannel.configureBlocking(false);
        	clientKey = clientChannel.register(serverKey.selector(), SelectionKey.OP_READ);
        	Channel ch = getChannel(clientKey);
        	if(head != null) {
    			head.onConnect(ch);
        	}
    	} catch(Exception ex) {
        	onError(clientKey, ex);
    	}
    }
    
    protected void onConnect(SelectionKey clientKey) {
    	try {
        	Channel ch = getChannel(clientKey);
        	if(head != null) {
    			head.onConnect(ch);
        	}
    	} catch(Exception e) {
        	onError(clientKey, e);
    	}
    }
    
    protected void onRead(SelectionKey clientKey) {
        try {
        	Channel ch = getChannel(clientKey);
        	if(!ch.isOpen()) {
        		return ;
        	}
        	int read = ch.readInternal();
        	if(read > 0) {
            	workerPool.submit(() -> {
	    			if(head != null) {
	    				try {
							head.onRead(ch, ch.readBytes());
						} catch (Exception e) {
				        	onError(clientKey, e);
						}
	    			}
            	});
        	} else if(read < 0) {
        		ch.prepareClose();
        		onClose(clientKey);
        	}
        } catch (Exception ex) {
        	onError(clientKey, ex);
        }
    }
 
    protected void onWrite(SelectionKey clientKey) {
        try {
        	Channel ch = getChannel(clientKey);
        	if(!ch.isOpen()) {
        		return ;
        	}
	    	ch.writeInternal();
        } catch (Exception e) {
        	onError(clientKey, e);
        }
    }
    
    public void onError(SelectionKey clientKey, Exception t) {
    	try {
			if(head != null) {
				Channel ch = getChannel(clientKey);
				head.onError(ch, t);
			}
		} catch (Exception e) {
			System.err.println("the last handler did not handle the exception. ");
			e.printStackTrace();
		}
    }
    

    public void onClose(SelectionKey clientKey) {
        try {
			if(head != null) {
				Channel ch = getChannel(clientKey);
				head.onDisconnect(ch);
				ch.close();
			}
        } catch (Exception ex) {
        	onError(clientKey, ex);
        }
    }
    
    public void add(Handler ...handlers) {
    	if(handlers.length == 0) {
    		return ;
    	}
    	int i = 0;
    	if(head == null) {
    		head = tail = handlers[0];
    		i++;
    	}
    	for(; i < handlers.length; i++) {
    		if(tail.isFinalHandler() && i < handlers.length - 1) {
    			throw new HandlerException(
    					"final-handler must be at last of the handler list.");
    		}
    		tail.next = handlers[i];
    		handlers[i].last = tail;
    		tail = handlers[i];
    	}
    }
    
    public void push(Handler handler) {
    	if(head == null) {
    		head = tail = handler;
    	} else {
    		handler.last = tail;
    		tail.next = handler;
    		tail = handler;
    	}
    }
    
    public void shift(Handler handler) {
    	if(head == null) {
    		head = tail = handler;
    	} else {
        	handler.next = head.next.last;
        	head.next.last = handler;
        	head.next = handler;
    	}
    }
    
    public void close() {
    	int workers = workerCount.decrementAndGet();
    	if(workers <= 0) {
        	workerPool.shutdown();
    	}
    }
    
    protected boolean isAlive() {
    	return !workerPool.isTerminated();
    }
    
    protected void reStart() {
    	if(workerPool.isTerminated()) {
    		workerPool = new ThreadPoolExecutor(
            		coreThreads,
            		maxThreads,
            		KEEP_ALIVE_TIME,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    Executors.defaultThreadFactory());
    		workerCount.incrementAndGet();
    	}
    }
    
    protected boolean handlerInitialized() {
    	return tail != null;
    }
    
    protected void handleWrite(Channel channel, Bytes out) throws Exception {
    	if(tail != null) {
    		tail.onWrite(channel, out);
    	}
    }
    
    protected Channel getChannel(SelectionKey clientKey) throws IOException {
    	if(clientKey == null) {
    		return null;
    	}
    	Channel ch = (Channel) clientKey.attachment();
    	if(ch == null) {
        	SocketChannel clientChannel = (SocketChannel) clientKey.channel();
        	InetSocketAddress remote = (InetSocketAddress) clientChannel.getRemoteAddress();
        	ch = new Channel(remote.getAddress().getHostAddress(), remote.getPort(), 
        			clientKey, this);
        	clientKey.attach(ch);
    	}
    	return ch;
    }
}
