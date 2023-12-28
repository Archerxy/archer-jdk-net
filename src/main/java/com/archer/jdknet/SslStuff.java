package com.archer.jdknet;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

final class SslStuff {
	
	ByteBuffer appData;

	ByteBuffer netData;

	ByteBuffer peerAppData;

	ByteBuffer peerNetData;
	
	SSLEngine engine;
	
	SslMessage sslMessage;
    
    public SslStuff(int appDataSize, int netDataSize, SSLEngine engine) {
    	appData = ByteBuffer.allocate(appDataSize);
    	netData = ByteBuffer.allocate(netDataSize);
        peerAppData = ByteBuffer.allocate(appDataSize);
        peerNetData = ByteBuffer.allocate(netDataSize);
        this.engine = engine;
        sslMessage = new SslMessage();
    }
    

    public static final TrustManager[] NULL_TRUSTED_MGR = new TrustManager[] { 
    new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public X509Certificate[] getAcceptedIssuers() {return null;}@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
    }};

    public static ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(sessionProposedCapacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    public static ByteBuffer handleBufferUnderflow(ByteBuffer buffer, int pactetSize) {
        if (pactetSize > buffer.capacity()) {
        	ByteBuffer replaceBuffer = ByteBuffer.allocate(pactetSize);
        	buffer.flip();
        	replaceBuffer.put(buffer);
        	return replaceBuffer;
        }
        return buffer;
    }
    
    public static void closeConnection(Channel channel, SslStuff stuff) 
			throws IOException  {
		SSLEngine engine = stuff.engine;
		if(engine != null) {
			engine.closeInbound();
			engine.closeOutbound();
		}
		channel.close();
    }
    
    public class SslMessage {

        private ReentrantLock packetLock = new ReentrantLock(true);
    	
        private Bytes bytes;
        
//        private volatile boolean running;
    	
    	public SslMessage() {
    		bytes = new Bytes();
//    		running = false;
    	}
    	
        
        public Bytes unwrap(Channel channel, SslStuff stuff, Bytes read) throws IOException {
            SSLEngine engine = stuff.engine;
            int appBufferSize = engine.getSession().getApplicationBufferSize();
            int packetBufferSize = engine.getSession().getPacketBufferSize();

            packetLock.lock();
            try {
//                if(running) {
//                	return null;
//                }
//                running = true;
                while(read.available() > 0) {
                	int len = getTlsPacketLength(read.byteAt(3), read.byteAt(4)) + 5;
                	if(len > read.available()) {
                		break ;
                	}
                	stuff.peerNetData.clear();
                	read.writeToByteBuffer(stuff.peerNetData, len);
                	stuff.peerNetData.flip();
                	while (stuff.peerNetData.hasRemaining()) {
                		stuff.peerAppData.clear();
                        SSLEngineResult result = engine.unwrap(stuff.peerNetData, stuff.peerAppData);
                        switch (result.getStatus()) {
                        case OK:
                        	stuff.peerAppData.flip();
                        	bytes.readFromByteBuffer(stuff.peerAppData);
                        	break;
                        case BUFFER_OVERFLOW:
                        	stuff.peerAppData = enlargeBuffer(stuff.peerAppData, appBufferSize);
                            break;
                        case BUFFER_UNDERFLOW:
                        	stuff.peerNetData = handleBufferUnderflow(stuff.peerNetData, packetBufferSize);
                            break;
                        case CLOSED:
                    		System.err.println(Thread.currentThread().getName()+": ssl closed");
                            closeConnection(channel, stuff);
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                    }
                }
                if(bytes.available() > 0) {
                	Bytes out = bytes;
                	bytes = new Bytes();
                    return out;
                }
                return null;
            } finally {
//                running = false;
                packetLock.unlock();
            }
        }

        public Bytes wrap(Channel channel, SslStuff stuff, Bytes out) throws IOException {
            SSLEngine engine = stuff.engine;
            int packetBufferSize = engine.getSession().getPacketBufferSize();
            Bytes write = new Bytes();
            
            stuff.appData.clear();
            out.writeToByteBuffer(stuff.appData);
            stuff.appData.flip();
            while (stuff.appData.hasRemaining()) {
                stuff.netData.clear();
                SSLEngineResult result = engine.wrap(stuff.appData, stuff.netData);
                switch (result.getStatus()) {
                case OK:
                    stuff.netData.flip();
                    while (stuff.netData.hasRemaining()) {
                    	write.readFromByteBuffer(stuff.netData);
                    }
                    break;
                case BUFFER_OVERFLOW:
                    stuff.netData = enlargeBuffer(stuff.netData, packetBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap. "
                    		+ "I don't think we should ever get here.");
                case CLOSED:
                    closeConnection(channel, stuff);
                    throw new SSLException("Ssl channel closed.");
                default:
                    throw new SSLException("Invalid SSL status: " + result.getStatus());
                }
            }
            return write;
        }
    	
        private int getTlsPacketLength(byte b0, byte b1) {
        	int i0 = b0, i1 = b1;
        	i0 = i0 < 0 ? i0 + 256 : i0;
        	i1 = i1 < 0 ? i1 + 256 : i1;
        	return (i0 << 8) | i1;
        }
    }
}