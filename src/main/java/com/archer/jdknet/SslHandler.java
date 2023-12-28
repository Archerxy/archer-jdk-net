package com.archer.jdknet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

import com.archer.jdknet.util.CertAndKeyUtil;

public class SslHandler extends Handler {
	
	private static final String PROTOCOL = "TLS";
	
	private static ConcurrentHashMap<Channel, SslStuff> sslBufferCache = new ConcurrentHashMap<>();
	
    private SSLContext context;
    
    private boolean trustPeer;
    
    public SslHandler() throws NoSuchAlgorithmException {
    	this(PROTOCOL);
    }
    
    public SslHandler(boolean trustPeer) throws NoSuchAlgorithmException {
    	this(PROTOCOL, trustPeer);
    }
    
    public SslHandler(String protocol) throws NoSuchAlgorithmException {
    	this(protocol, false);
    }
    
    public SslHandler(String protocol, boolean trustPeer) throws NoSuchAlgorithmException {
        this.context = SSLContext.getInstance(protocol);
        this.trustPeer = trustPeer;
    }
    
    public SslHandler(SSLContext context) {
    	this.context = context;
    }
    
    public void init(String caPath, String keyPath, String crtPath) throws Exception {
		try(InputStream caIn = new FileInputStream(caPath);
			InputStream keyIn = new FileInputStream(keyPath);
			InputStream crtIn = new FileInputStream(crtPath);) {
			init(caIn, keyIn, crtIn);
		}
    }
    
    public void init(InputStream caStream, InputStream keyStream, InputStream crtStream) throws Exception {
    	init(CertAndKeyUtil.buildKeyManagers(keyStream, crtStream, null), 
    			CertAndKeyUtil.buildTrustManagers(caStream));
    }
    
    public void init(KeyManager[] km, TrustManager[] tm) throws KeyManagementException {
    	if(trustPeer) {
        	context.init(km , SslStuff.NULL_TRUSTED_MGR, null);
    	} else {
        	context.init(km , tm, null);
    	}
    }


    private boolean doHandshake(SocketChannel socketChannel, SslStuff stuff) 
    		throws IOException {
        SSLEngine engine = stuff.engine;
        engine.beginHandshake();
        
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        int packetBufferSize = engine.getSession().getPacketBufferSize();
        
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
        stuff.netData.clear();
        stuff.peerNetData.clear();

        SSLEngineResult result;
        HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != HandshakeStatus.FINISHED
        		&& handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
            case NEED_UNWRAP:
                if (socketChannel.read(stuff.peerNetData) < 0) {
                    if (engine.isInboundDone() && engine.isOutboundDone()) {
                        return false;
                    }
                    try {
                        engine.closeInbound();
                    } catch (SSLException e) {
                    	e.printStackTrace();
                        System.err.println("This engine was forced to close inbound, "
                        	+ "without having received the proper "
                        	+ "SSL/TLS close notification message "
                        	+ "from the peer, due to end of stream.");
                    }
                    engine.closeOutbound();
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                }
                stuff.peerNetData.flip();
            	result = engine.unwrap(stuff.peerNetData, peerAppData);
                stuff.peerNetData.compact();
                handshakeStatus = result.getHandshakeStatus();
                switch (result.getStatus()) {
                case OK:
                    break;
                case BUFFER_OVERFLOW:
                    peerAppData = SslStuff.enlargeBuffer(peerAppData, appBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                	stuff.peerNetData = SslStuff.handleBufferUnderflow(stuff.peerNetData, packetBufferSize);
                    break;
                case CLOSED:
                    if (engine.isOutboundDone()) {
                        return false;
                    } else {
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
                break;
            case NEED_WRAP:
                stuff.netData.clear();
                result = engine.wrap(myAppData, stuff.netData);
                handshakeStatus = result.getHandshakeStatus();
                switch (result.getStatus()) {
                case OK :
                	stuff.netData.flip();
                    while (stuff.netData.hasRemaining()) {
                        socketChannel.write(stuff.netData);
                    }
                    break;
                case BUFFER_OVERFLOW:
                    stuff.netData = SslStuff.enlargeBuffer(stuff.netData, packetBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap.");
                case CLOSED:
                    try {
                    	stuff.netData.flip();
                        while (stuff.netData.hasRemaining()) {
                            socketChannel.write(stuff.netData);
                        }
                        stuff.peerNetData.clear();
                    } catch (IOException e) {
                        System.err.println("Failed to send server's CLOSE message "
                        		+ "due to socket channel's failure.");
                        handshakeStatus = engine.getHandshakeStatus();
                    }
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
                break;
            case NEED_TASK:
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
                handshakeStatus = engine.getHandshakeStatus();
                break;
            case FINISHED:
            case NOT_HANDSHAKING:
                break;
            default:
                throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
            }
        }
        return true;
    }

	@Override
	public void onConnect(Channel channel) throws Exception {
		SslStuff stuff = getBufSet(channel);
		if(doHandshake(channel.socketChannel(), stuff)) {
			toNextOnConnect(channel);
		} else {
			throw new ChannelException("ssl handshake failed.");
		}
	}

	
	@Override
	public void onRead(Channel channel, Bytes in) throws Exception {
		SslStuff stuff = getBufSet(channel);
		Bytes read = stuff.sslMessage.unwrap(channel, stuff, in);
		if(read != null) {
			toNextOnRead(channel, read);
		}
	}
	
	@Override
	public void onWrite(Channel channel, Bytes out) throws Exception {
		SslStuff stuff = getBufSet(channel);
		Bytes appOut = stuff.sslMessage.wrap(channel, stuff, out);
		toLastOnWrite(channel, appOut);
	}

	@Override
	public void onDisconnect(Channel channel) throws Exception {
		sslBufferCache.remove(channel);
		toNextOnDisconnect(channel);
	}

	@Override
	public void onError(Channel channel, Throwable t) {
		toNextOnError(channel, t);
	}
	

	@Override
	public boolean isFinalHandler() {
		return false;
	}

	private SslStuff getBufSet(Channel channel) {
		SslStuff stuff = sslBufferCache.getOrDefault(channel, null);
		if(stuff == null) {
			SSLEngine engine = context.createSSLEngine();
			engine.setUseClientMode(channel.isClientMode());
			stuff = new SslStuff(Bytes.BUFFER_SIZE, engine.getSession().getPacketBufferSize(), engine);
			sslBufferCache.put(channel, stuff);
		}
		return stuff;
	}
}
