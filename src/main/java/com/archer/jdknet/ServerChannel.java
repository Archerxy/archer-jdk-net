package com.archer.jdknet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;

public class ServerChannel {
	
	private Selector selector;
	
    private ServerSocketChannel serverChannel;
    
    private volatile boolean running;
    
    private HandlerWorker worker;
 
    private ServerWorkerThread workerThread;
    
    private int port;
    
    public ServerChannel() {
        worker = new HandlerWorker(1, 1);
        workerThread = new ServerWorkerThread(this);
    }
    
    public ServerChannel(int coreThreads, int maxThreads) {
        worker = new HandlerWorker(coreThreads, maxThreads);
        workerThread = new ServerWorkerThread(this);
    }
    
    public ServerChannel bind(int port) {
    	this.port = port;
        return this;
    }
 
    public void start() throws IOException {
    	if(selector == null || !selector.isOpen()) {
            selector = Selector.open();
    	}
        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        if(!worker.isAlive()) {
        	worker.reStart();
        }
    	workerThread.start();
    }
    
    public void stop() throws IOException {
    	running = false;
    	serverChannel.close();
    	selector.close();
    }
    
    public boolean isAlive() {
    	return running;
    }

    public ServerChannel add(Handler ...handlers) {
    	worker.add(handlers);
    	return this;
    }
    
    public ServerChannel push(Handler handler) {
    	worker.push(handler);
    	return this;
    }
    
    public ServerChannel shift(Handler handler) {
    	worker.shift(handler);
    	return this;
    }
    
    public HandlerWorker handlerWorker() {
    	return worker;
    }
    
    public boolean handlerInitialized() {
    	return worker.handlerInitialized();
    }

    private void run() {
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
                    	handle(sk);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                serverChannel.close();
                selector.close();
            } catch(Exception ignore) {}
            worker.close();
        }
    }
    
    private void handle(SelectionKey sk) {
    	try {
    		if(sk.isAcceptable()) {
    			worker.onAccept(sk);
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
    
    private static class ServerWorkerThread extends Thread {
    	
    	ServerChannel server;
    	
    	public ServerWorkerThread(ServerChannel server) {
    		this.server = server;
    	}
    	
    	@Override
    	public void run() {
    		server.run();
    	}
    }
}
