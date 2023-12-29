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
	
    private static final int KEEP_ALIVE_TIME = 10_000;
    private static ThreadPoolExecutor workerPool;
    private static AtomicInteger workerCount;
	
    private Handler head;
    private Handler tail;
    
    private int coreThreads, maxThreads;
    private boolean enableThreads;
    
    public HandlerWorker() {
    	this(0, 0, false);
    }
    
    public HandlerWorker(int coreThreads, int maxThreads) {
    	this(coreThreads, maxThreads, true);
    }
    
    public HandlerWorker(int coreThreads, int maxThreads, boolean enableThreads) {
    	this.enableThreads = enableThreads;
    	if(enableThreads) {
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
        	workerCount = new AtomicInteger(1);
    	} else {
        	this.coreThreads = 0;
        	this.maxThreads = 0;
    	}
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
        	Bytes readBytes;
        	try {
            	readBytes = ch.readInternal();
        	} catch (IOException ex) {
        		ch.prepareClose();
        		onClose(clientKey);
        		return ;
            }
        	if(readBytes != null && readBytes.available() > 0) {
        		if(this.enableThreads) {
                	workerPool.submit(() -> {
                		handle(clientKey, ch, readBytes);
                	});
        		} else {
            		handle(clientKey, ch, readBytes);
        		}
        	}
        	if(!ch.isOpen()) {
        		ch.prepareClose();
        		onClose(clientKey);
        	}
        }  catch(Exception e) {
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
    	if(this.enableThreads) {
        	int workers = workerCount.decrementAndGet();
        	if(workers <= 0) {
            	workerPool.shutdown();
        	}
    	}
    }
    
    private void handle(SelectionKey clientKey, Channel ch, Bytes readBytes) {
		if(head != null) {
			try {
				head.onRead(ch, readBytes);
			} catch (Exception e) {
	        	onError(clientKey, e);
			}
		}
    }
    
    protected boolean isAlive() {
    	if(enableThreads) {
        	return !workerPool.isTerminated();
    	}
    	return true;
    }
    
    protected void reStart() {
    	if(this.enableThreads) {
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
