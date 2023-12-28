package com.archer.jdknet.http.client;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.Channel;
import com.archer.jdknet.ClientChannel;
import com.archer.jdknet.Handler;
import com.archer.jdknet.SslHandler;
import com.archer.jdknet.http.HttpException;
import com.archer.jdknet.http.HttpStatus;


/**
 * @author xuyi
 */
public class Request {

    private static final int TIMEOUT = 3000;
    private static final int BASE_HEADER_LEN = 128;
    private static final int DEFAULT_HEADER_SIZE = 12;

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
	
    public static Response get(String httpUrl) throws IOException {
        return get(httpUrl, null);
    }

    public static Response post(String httpUrl, byte[] body) throws IOException {
        return post(httpUrl, body, null);
    }

    public static Response put(String httpUrl, byte[] body) throws IOException {
        return put(httpUrl, body, null);
    }

    public static Response delete(String httpUrl, byte[] body) throws IOException {
        return delete(httpUrl, body, null);
    }

    public static Response get(String httpUrl, Options options) throws IOException {
        return request("GET", httpUrl, null, options);
    }

    public static Response post(String httpUrl, byte[] body, Options options) throws IOException {
        return request("POST", httpUrl, body, options);
    }

    public static Response put(String httpUrl, byte[] body, Options options) throws IOException {
        return request("PUT", httpUrl, body, options);
    }

    public static Response delete(String httpUrl, byte[] body, Options options) throws IOException {
        return request("DELETE", httpUrl, body, options);
    }
	
	public static Response request(String method, String httpUrl, byte[] body, Options option)
			throws IOException {
		if(option == null) {
			option = new Options();
		}
		HttpUrl url = HttpUrl.parse(httpUrl);
		
		ClientChannel channel = new ClientChannel(url.getHost(), url.getPort());
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
			channel.add(new SslHandler(ctx));
		}

		byte[] data = getRequestAsBytes(method, url, option, body);
		try {
			HttpRequestHandler reqHandler = new HttpRequestHandler(new Bytes(data));
			channel.add(reqHandler);
			channel.connect();
			reqHandler.awaitForResponse(option.getTimeout());
			if(reqHandler.getErr() != null) {
				throw reqHandler.getErr();
			}
			
			return reqHandler.getRes();
		} finally {
			channel.close();
		}
    	
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
    	
    	private int timeout = TIMEOUT;
    	
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

		public int getTimeout() {
			return timeout;
		}

		public Options timeout(int timeout) {
			this.timeout = timeout;
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
	

	private static class HttpRequestHandler extends Handler {
		
		private Object lock = new Object();
		
		private Response res;
		private HttpException err;
		private Bytes req;
		
		public HttpRequestHandler(Bytes req) {
			this.req = req;
		}
		
		public void awaitForResponse(long timeout) {
			long s = System.currentTimeMillis();
			synchronized(lock) {
				try {
					lock.wait(timeout);
				} catch (InterruptedException ignore) {}
			}
			long e = System.currentTimeMillis();
			if(e - s >= timeout) {
				throw new HttpException(HttpStatus.GATEWAY_TIMEOUT.getCode(), "timeout " + (e - s));
			}
		}
		
		public void release() {
			synchronized(lock) {
				lock.notifyAll();
			}
		}

		public HttpException getErr() {
			return err;
		}

		public Response getRes() {
			return res;
		}

		@Override
		public void onConnect(Channel channel) throws Exception {
			toLastOnWrite(channel, req);
		}

		@Override
		public void onRead(Channel channel, Bytes in) throws Exception {
			if(res == null) {
				res = new Response();
			}
			if(res.headerParsed()) {
				res.parseContent(in.readAll());
			} else {
				res.parseHead(in.readAll());
			}
			if(res.finished()) {
				release();
			}
		}

		@Override
		public void onWrite(Channel channel, Bytes out) throws Exception {}

		@Override
		public void onDisconnect(Channel channel) throws Exception {}

		@Override
		public void onError(Channel channel, Throwable t) {
			err = new HttpException(HttpStatus.SERVICE_UNAVAILABLE.getCode(), t.getMessage());
			release();
		}

		@Override
		public boolean isFinalHandler() {return true;}
		
	}
}
