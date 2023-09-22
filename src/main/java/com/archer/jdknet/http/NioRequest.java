package com.archer.jdknet.http;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author xuyi
 */
public class NioRequest {

    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 3000;
    private static final int BASE_HEADER_LEN = 128;
    private static final int BUFFER_SIZE = 10 * 1024;
    private static final int DEFAULT_HEADER_SIZE = 12;
    private static final int READ_TRY = 3;
    
    private static final int CONNECT_STATE = 1;
    private static final int READ_STATE = 3;

	private static final char COLON = ':';
	private static final char SPACE = ' ';
	private static final String ENTER = "\r\n";
	
	private static final String HTTP_PROTOCOL = "HTTP/1.1";
	private static final String HEADER_CONTENT_LENGTH = "content-length";
	private static final String HEADER_CONTENT_ENCODE = "content-encoding";
	private static final String HEADER_HOST = "host";
	private static final String DEFAULT_CONTENT_ENCODE = "utf-8";
	private static final String[] HEADER_KEY = {"user-agent", "connection", "content-type", "accept"};
	private static final String[] HEADER_VAL = 
		{"Java/"+System.getProperty("java.version"), "close", "application/x-www-form-urlencoded",
		 "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2"};
	
	private static final String SSL_PROTOCOL = "TLSv1.2";
	
	private static Selector selector;
	private static final AtomicBoolean running = new AtomicBoolean(false);
	
	static {
		try {
			selector = Selector.open();
		} catch (IOException ignore) {}
	}


    public static NioResponse get(String httpUrl) throws IOException {
        return get(httpUrl, null);
    }

    public static NioResponse post(String httpUrl, byte[] body) throws IOException {
        return post(httpUrl, body, null);
    }

    public static NioResponse put(String httpUrl, byte[] body) throws IOException {
        return put(httpUrl, body, null);
    }

    public static NioResponse delete(String httpUrl, byte[] body) throws IOException {
        return delete(httpUrl, body, null);
    }

    public static NioResponse get(String httpUrl, Options options) throws IOException {
        return request("GET", httpUrl, null, options);
    }

    public static NioResponse post(String httpUrl, byte[] body, Options options) throws IOException {
        return request("POST", httpUrl, body, options);
    }

    public static NioResponse put(String httpUrl, byte[] body, Options options) throws IOException {
        return request("PUT", httpUrl, body, options);
    }

    public static NioResponse delete(String httpUrl, byte[] body, Options options) throws IOException {
        return request("DELETE", httpUrl, body, options);
    }
	
	public static NioResponse request(String method, String httpUrl, byte[] body, Options option)
			throws IOException {
		if(option == null) {
			option = new Options();
		}
		HttpUrl url = HttpUrl.parse(httpUrl);
    	
    	SSLEngine engine = null;
    	BufferSet buf = null;
    	if(url.isHttps()) {
    		SSLContext ctx = null;
    		try {
    			ctx = SSLContext.getInstance(option.getSslProtocol());
    		} catch(NoSuchAlgorithmException e) {
    			throw new SSLException("known ssl protocol " + option.getSslProtocol());
    		}
    		TrustManager[] trustManager;
    		if(option.isVerifyCert()) {
    			trustManager = option.getTrustManager();
    		} else {
    			trustManager = NULL_TRUSTED_MGR;
    		}
			try {
				ctx.init(option.getKeyManager(), trustManager, null);
			} catch (KeyManagementException e) {
				throw new IOException(e);
			}
    		engine = ctx.createSSLEngine(url.getHost(), url.getPort());
            engine.setUseClientMode(true);
			buf = new BufferSet(BUFFER_SIZE, engine.getSession().getPacketBufferSize());
    	}

		SocketChannel socketChannel = SocketChannel.open();
    	socketChannel.configureBlocking(false);
    	SelectionKey key = socketChannel.register(selector, 
    			SelectionKey.OP_CONNECT);
    	socketChannel.connect(url.getAddress());
    	
		try {
	    	long start = System.currentTimeMillis(), end;
	    	int state = CONNECT_STATE;
	    	boolean sended = false;
	    	while(true) {
	    		end = System.currentTimeMillis();
	    		if(end >= start + option.getConnectTimeout() && state == CONNECT_STATE) {
	    			throw new IOException("connect timeout");
	    		}
	    		if(end >= start + option.getReadTimeout() && state == READ_STATE) {
	    			throw new IOException("read timeout");
	    		}
	    		
	    		doSelect(option.getConnectTimeout());
	    		if(key.isConnectable() && socketChannel.finishConnect()) {
	    			key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
	    			if(url.isHttps()) {
	    				engine.beginHandshake();
	    				doHandshake(socketChannel, engine, buf);
	    			}
	    			start = System.currentTimeMillis();
	    			state = READ_STATE;
	    		}
	    		if(key.isReadable()) {
	    			byte[] res;
	    			if(url.isHttps()) {
	    				res = read(socketChannel, engine, buf);
	    			} else {
		    			res = read(socketChannel);
	    			}
	    			return NioResponse.parseResponseBytes(res);
	    		}
	    		if(key.isWritable() && !sended) {
	    			byte[] data = getRequestAsBytes(method, url, option, body);
	    			if(url.isHttps()) {
	    				write(socketChannel, engine, data, buf);
	    			} else {
		    	    	write(socketChannel, data);
	    			}
	    			sended = true;
	    			key.interestOps(SelectionKey.OP_READ);
	    		}
	    	}
		} finally {
			closeConnection(socketChannel, engine, buf);
		}
	}
	
	private static void doSelect(long timeout) {
		if(running.compareAndSet(false, true)) {
			try {
				selector.select(timeout);
			} catch(IOException ignore) {}
		}
		running.compareAndSet(true, false);
	}
	
	private static void write(SocketChannel socketChannel, byte[] data) throws IOException {
		ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
		byteBuf.put(data);
		byteBuf.flip();
		while(byteBuf.hasRemaining()) {
			socketChannel.write(byteBuf);
		}
	}
	
	private static byte[] read(SocketChannel socketChannel) 
			throws IOException {
		ByteBuffer byteBuf = ByteBuffer.allocate(BUFFER_SIZE);
		byte[] t = new byte[BUFFER_SIZE];
		int offset = 0, readCount = 0;
		while(readCount < READ_TRY) {
			byteBuf.clear();
			int bytesRead = socketChannel.read(byteBuf);
			if(bytesRead > 0) {
				readCount = 0;
				byteBuf.flip();
				if(t.length < offset + byteBuf.limit()) {
					int newLen = t.length << 1;
					while(newLen < offset + byteBuf.limit()) {
						newLen <<= 1;
					}
					byte[] tmp = new byte[newLen];
					System.arraycopy(t, 0, tmp, 0, offset);
					t = tmp;
				}
				byteBuf.get(t, offset, byteBuf.limit());
				offset += byteBuf.limit();
			} else if(bytesRead == 0) {
				readCount++;
			} else {
				break;
			}
		}
		if(offset == 0) {
			throw new IOException("Unexpected end of file from server");
		}
		return Arrays.copyOfRange(t, 0, offset);
	}
	
	private static void write(SocketChannel socketChannel, 
							  SSLEngine engine, 
							  byte[] data,
							  BufferSet buf) throws IOException {
        int packetBufferSize = engine.getSession().getPacketBufferSize();
        int offset = 0;
        while(offset < data.length) {
            buf.appData.clear();
        	if(offset + buf.appData.capacity() < data.length) {
                buf.appData.put(data, offset, buf.appData.capacity());
                offset += buf.appData.capacity();
        	} else {
                buf.appData.put(data, offset, data.length - offset);
                offset = data.length;
        	}
            buf.appData.flip();
            while (buf.appData.hasRemaining()) {
                buf.netData.clear();
                SSLEngineResult result = engine.wrap(buf.appData, buf.netData);
                switch (result.getStatus()) {
                case OK:
                	buf.netData.flip();
                    while (buf.netData.hasRemaining()) {
                        socketChannel.write(buf.netData);
                    }
                    break;
                case BUFFER_OVERFLOW:
                	buf.netData = enlargeBuffer(buf.netData, packetBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap. "
                    		+ "I don't think we should ever get here.");
                case CLOSED:
                    closeConnection(socketChannel, engine, buf);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        }

    }

	private static byte[] read(SocketChannel socketChannel, 
							 SSLEngine engine, 
							 BufferSet buf) throws IOException  {
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        int packetBufferSize = engine.getSession().getPacketBufferSize();
        int offset = 0, readCount = 0;
        byte[] t = new byte[BUFFER_SIZE];
        while (readCount < READ_TRY) {
        	buf.peerNetData.clear();
            int bytesRead = socketChannel.read(buf.peerNetData);
            if (bytesRead > 0) {
            	readCount = 0;
            	buf.peerNetData.flip();
                while (buf.peerNetData.hasRemaining()) {
                	buf.peerAppData.clear();
                    SSLEngineResult result = engine.unwrap(buf.peerNetData, buf.peerAppData);
                    switch (result.getStatus()) {
                    case OK:
                    	buf.peerAppData.flip();
                    	if(t.length < offset + buf.peerAppData.remaining()) {
                    		int newLen = t.length << 1;
                    		while(newLen < offset + buf.peerAppData.remaining()) {
                    			newLen <<= 1;
                    		}
        					byte[] tmp = new byte[newLen];
        					System.arraycopy(t, 0, tmp, 0, offset);
        					t = tmp;
                    	}
                    	System.arraycopy(buf.peerAppData.array(), 0, t, offset, 
                    			buf.peerAppData.remaining());
                    	offset += buf.peerAppData.remaining();
                    	break;
                    case BUFFER_OVERFLOW:
                    	buf.peerAppData = enlargeBuffer(buf.peerAppData, appBufferSize);
                        break;
                    case BUFFER_UNDERFLOW:
                    	buf.peerNetData = handleBufferUnderflow(buf.peerNetData, packetBufferSize);
                        break;
                    case CLOSED:
                        closeConnection(socketChannel, engine, buf);
                        throw new SSLException("ssl closed.");
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + 
                        		result.getStatus());
                    }
                }
            } else if (bytesRead < 0) {
            	try {
                    engine.closeInbound();
                } catch (SSLException ignore) {}
            	break;
            } else {
            	readCount++;
            }
        }
		if(offset == 0) {
			throw new IOException("Unexpected end of file from server");
		}
        return Arrays.copyOfRange(t, 0, offset);
    }

	
    private static boolean doHandshake(SocketChannel socketChannel, 
    								   SSLEngine engine, 
    								   BufferSet buf) throws IOException {
        SSLEngineResult result;
        HandshakeStatus handshakeStatus;
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        int packetBufferSize = engine.getSession().getPacketBufferSize();
        
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
        buf.netData.clear();
        buf.peerNetData.clear();

        handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != HandshakeStatus.FINISHED
        		&& handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
            case NEED_UNWRAP:
                if (socketChannel.read(buf.peerNetData) < 0) {
                    if (engine.isInboundDone() && engine.isOutboundDone()) {
                        return false;
                    }
                    try {
                        engine.closeInbound();
                    } catch (SSLException e) {
                        System.err.println("This engine was forced to close inbound, "
                        	+ "without having received the proper "
                        	+ "SSL/TLS close notification message "
                        	+ "from the peer, due to end of stream.");
                    }
                    engine.closeOutbound();
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                }
                buf.peerNetData.flip();
            	result = engine.unwrap(buf.peerNetData, peerAppData);
                buf.peerNetData.compact();
                handshakeStatus = result.getHandshakeStatus();
                switch (result.getStatus()) {
                case OK:
                    break;
                case BUFFER_OVERFLOW:
                    peerAppData = enlargeBuffer(peerAppData, appBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                	buf.peerNetData = handleBufferUnderflow(buf.peerNetData, packetBufferSize);
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
                buf.netData.clear();
                result = engine.wrap(myAppData, buf.netData);
                handshakeStatus = result.getHandshakeStatus();
                switch (result.getStatus()) {
                case OK :
                	buf.netData.flip();
                    while (buf.netData.hasRemaining()) {
                        socketChannel.write(buf.netData);
                    }
                    break;
                case BUFFER_OVERFLOW:
                    buf.netData = enlargeBuffer(buf.netData, packetBufferSize);
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap.");
                case CLOSED:
                    try {
                    	buf.netData.flip();
                        while (buf.netData.hasRemaining()) {
                            socketChannel.write(buf.netData);
                        }
                        buf.peerNetData.clear();
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

    private static ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(sessionProposedCapacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    private static ByteBuffer handleBufferUnderflow(ByteBuffer buffer, int pactetSize) {
        if (pactetSize > buffer.capacity()) {
        	ByteBuffer replaceBuffer = ByteBuffer.allocate(pactetSize);
        	buffer.flip();
        	replaceBuffer.put(buffer);
        	return replaceBuffer;
        }
        return buffer;
    }

    private static void closeConnection(SocketChannel socketChannel, 
    									SSLEngine engine,
    									BufferSet buf) 
    											throws IOException  {
    	if(engine != null) {
        	doHandshake(socketChannel, engine, buf);
        	engine.closeOutbound();
    	}
        socketChannel.close();
    }
   
	private static byte[] getRequestAsBytes(String method, HttpUrl url, Options option, byte[] body) {
		Map<String, String> headers = option.getHeaders();
		Map<String, String> newHeaders = new HashMap<>(DEFAULT_HEADER_SIZE);
		if(headers != null && headers.size() > 0) {
			for(Map.Entry<String, String> header: headers.entrySet()) {
				newHeaders.put(header.getKey().toLowerCase(Locale.ROOT), header.getValue());
			}
		}
		StringBuilder sb = new StringBuilder(BASE_HEADER_LEN * (newHeaders.size() + 3));
		sb.append(method).append(SPACE).append(url.getUri()).append(SPACE).append(HTTP_PROTOCOL).append(ENTER);
		sb.append(HEADER_HOST).append(COLON).append(SPACE)
				.append(url.getHost()).append(COLON).append(url.getPort()).append(ENTER);
		for(int i = 0 ; i < HEADER_KEY.length; i++) {
			sb.append(HEADER_KEY[i]).append(COLON).append(SPACE);
			if(newHeaders.containsKey(HEADER_KEY[i])) {
				sb.append(newHeaders.get(HEADER_KEY[i])).append(ENTER);
				newHeaders.remove(HEADER_KEY[i]);
			} else {
				sb.append(HEADER_VAL[i]).append(ENTER);
			}
		}
		for(Map.Entry<String, String> header: newHeaders.entrySet()) {
			sb.append(header.getKey()).append(COLON).append(SPACE).append(header.getValue()).append(ENTER);
		}
		if(body != null) {
			sb.append(HEADER_CONTENT_LENGTH).append(COLON).append(SPACE).append(body.length).append(ENTER);
			sb.append(HEADER_CONTENT_ENCODE).append(COLON).append(SPACE).append(option.getEncoding()).append(ENTER);
		}
		sb.append(ENTER);
		byte[] headerBytes = sb.toString().getBytes();
		byte[] requestBytes;
		if(body != null) {
			requestBytes = new byte[headerBytes.length + body.length];
			System.arraycopy(headerBytes, 0, requestBytes, 0, headerBytes.length);
			System.arraycopy(body, 0, requestBytes, headerBytes.length, body.length);
		} else {
			requestBytes = headerBytes;
		}
		return requestBytes;
	}

    private static final TrustManager[] NULL_TRUSTED_MGR = new TrustManager[] { 
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
	
	public static class Options {
    	
    	private boolean verifyCert = true;
    	
    	private Map<String, String> headers = null;
    	
    	private int connectTimeout = CONNECT_TIMEOUT;
    	
    	private int readTimeout = READ_TIMEOUT;
    	
    	private String sslProtocol = SSL_PROTOCOL;

		private String encoding = DEFAULT_CONTENT_ENCODE;
    	
    	private KeyManager[] keyManager;
    	
    	private TrustManager[] trustManager;
    	
    	public Options() {}
    	
		public boolean isVerifyCert() {
			return verifyCert;
		}

		public Options verifyCert(boolean verifyCert) {
			this.verifyCert = verifyCert;
			return this;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public Options headers(Map<String, String> headers) {
			this.headers = headers;
			return this;
		}

		public int getConnectTimeout() {
			return connectTimeout;
		}

		public Options connectTimeout(int connectTimeout) {
			this.connectTimeout = connectTimeout;
			return this;
		}

		public int getReadTimeout() {
			return readTimeout;
		}

		public Options readTimeout(int readTimeout) {
			this.readTimeout = readTimeout;
			return this;
		}

		public String getEncoding() {
			return encoding;
		}

		public Options encoding(String encoding) {
			this.encoding = encoding;
			return this;
		}

		public String getSslProtocol() {
			return sslProtocol;
		}
		
		public Options sslProtocol(String sslProtocol) {
			this.sslProtocol = sslProtocol;
			return this;
		}

		public KeyManager[] getKeyManager() {
			return keyManager;
		}

		public Options keyManager(KeyManager[] keyManager) {
			this.keyManager = keyManager;
			return this;
		}

		public TrustManager[] getTrustManager() {
			return trustManager;
		}

		public Options trustManager(TrustManager[] trustManager) {
			this.trustManager = trustManager;
			return this;
		}
	}

	final static class BufferSet {
		
		ByteBuffer appData;

		ByteBuffer netData;

		ByteBuffer peerAppData;

		ByteBuffer peerNetData;
	    
	    public BufferSet(int appDataSize, int netDataSize) {
	    	appData = ByteBuffer.allocate(appDataSize);
	    	netData = ByteBuffer.allocate(netDataSize);
	        peerAppData = ByteBuffer.allocate(appDataSize);
	        peerNetData = ByteBuffer.allocate(netDataSize);
	    }
	}
	
	final static class HttpUrl {

		private String url;

		private String protocol;

		private String host;

		private int port;

		private InetSocketAddress address;

		private String uri;

		private HttpUrl(String url, String protocol, String host, int port, String uri) {
			this.url = url;
			this.protocol = protocol;
			this.host = host;
			this.port = port;
			this.address = new InetSocketAddress(host, port);
			this.uri = uri;
		}

		public String getUrl() {
			return url;
		}
		
		public String getProtocol() {
			return protocol;
		}
		
		public String getHost() {
			return host;
		}

		public int getPort() {
			return port;
		}

		public InetSocketAddress getAddress() {
			return address;
		}

		public String getUri() {
			return uri;
		}
		
		public boolean isHttps() {
			return PROTOCOL_HTTPS.equals(protocol);
		}
		
		private static final char COLON = ':';
		private static final char SLASH = '/';

	    private static final char[] HTTP = { 'h', 't', 't', 'p' };
	    private static final char[] HTTPS = { 'h', 't', 't', 'p', 's' };
	    
	    private static final char[] PROTOCOL_SEP = { ':', '/', '/' };
	    
	    private static final String PROTOCOL_HTTP = "http";
	    private static final String PROTOCOL_HTTPS = "https";
	    
		
		public static HttpUrl parse(String httpUrl) {
			if(httpUrl == null || httpUrl.length() < HTTP.length + PROTOCOL_SEP.length + 2) {
				throw new IllegalArgumentException("invalid http url " + httpUrl);
			}
			String protocol = null, host = null, uri = null;
			int port = 80;
			char[] urlChars = httpUrl.toCharArray();
			int i = 0, t = 0;
			for(; i < HTTP.length; i++) {
				if(urlChars[i] != HTTP[i]) {
					throw new IllegalArgumentException("invalid http url " + httpUrl);
				}
			}
			if(urlChars[i] == HTTPS[i]) {
				protocol = PROTOCOL_HTTPS;
				i++;
			} else {
				protocol = PROTOCOL_HTTP;
			}
			if(PROTOCOL_HTTPS.equals(protocol)) {
				port = 443;
			}
			t = i;
			for(; i < t + PROTOCOL_SEP.length; i++ ) {
				if(urlChars[i] != PROTOCOL_SEP[i - t]) {
					throw new IllegalArgumentException("invalid http url " + httpUrl);
				}
			}
			try {
				t = i;
				for(; i < urlChars.length; i++) {
					if(urlChars[i] == COLON) {
						char[] hostChars = Arrays.copyOfRange(urlChars, t, i);
						host = new String(hostChars);
						t = i + 1;
						continue;
					}
					if(urlChars[i] == SLASH) {
						if(host == null) {
							char[] hostChars = Arrays.copyOfRange(urlChars, t, i);
							host = new String(hostChars);
						} else {
							char[] portChars = Arrays.copyOfRange(urlChars, t, i);
							port = Integer.parseInt(new String(portChars));
						}
						break;
					}
				}
			} catch(Exception e) {
				throw new IllegalArgumentException("invalid http url " + httpUrl);
			}
			uri = new String(Arrays.copyOfRange(urlChars, i, urlChars.length));
            if (uri.length() == 0) {
            	uri = "/";
            } else if (uri.charAt(0) == '?') {
            	uri = "/" + uri;
            }
			if(host == null) {
				throw new IllegalArgumentException("invalid http url " + httpUrl);
			}
			return new HttpUrl(httpUrl, protocol, host, port, uri);
		}
	}
}
