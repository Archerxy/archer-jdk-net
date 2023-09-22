package com.archer.jdknet.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.util.HexUtil;

public class HttpRequest {

    private static final int DEFAULT_HEADER_SIZE = 32;
    private static final char SEM = ';';
    private static final char COLON = ':';
    private static final char SPACE = ' ';
    private static final char ENTER = '\n';
    private static final char QUM = '?';
    private static final char EQ = '=';
    private static final char AND = '&';
    private static final int KEY_START = 1;
    private static final int VAL_START = 2;
    private static final int CHUNKED_LEN = 3;
    private static final int CHUNKED_VAL = 4;
    
	protected static final String GET = "GET";
	protected static final String POST = "POST";
	protected static final String PUT = "PUT";
	protected static final String DELETE = "DELETE";
	protected static final String OPTION = "OPTION";

	protected static final char[] VERSION = { 'H', 'T', 'T', 'P', '/', '1', '.'};
	protected static final char URI_SEP = '/';
	
	protected static final int METHOD_LEN = OPTION.length() + 2;

	private static final String HEADER_CONTENT_TYPE = "content-type";
	private static final String HEADER_CONTENT_LENGTH = "content-length";
	private static final String HEADER_CONTENT_ENCODE = "content-encoding";
	private static final String HEADER_TRANSFER_ENCODING = "transfer-encoding";

	private static final String CHUNKED = "chunked";

    private static final String DEFAULT_ENCODING_VAL = "utf-8";
    private static final String DEFAULT_ENCODING_KEY = "charset";
    
    private ReentrantLock contentLock = new ReentrantLock(true);
	
    private volatile boolean finished = false; 
    
    private Bytes remainBody = new Bytes();
    private Bytes chunkedBody = new Bytes();

    private String remoteHost;
    private int remotePort;
    
	private String method;
	private String uri;
	private String httpVersion;

	private Map<String, String> query;
	
	private Map<String, String> headers;
	private String contentType;
	private String contentEncoding;
	private int contentLength;
	private boolean isChunked;

	private byte[] content;
	private int pos = 0;

	protected HttpRequest(String host, int port) {
		clear();
		remoteHost = host;
		remotePort = port; 
	}

	public String remoteHost() {
		return remoteHost;
	}

	public int remotePort() {
		return remotePort;
	}

	
	public String getMethod() {
		return method;
	}

	public String getUri() {
		return uri;
	}

	public String getHttpVersion() {
		return httpVersion;
	}

	public String getHeader(String key) {
		return headers.getOrDefault(key, null);
	}
	
	public Map<String, String> getQueryParams() {
		return query;
	}
	
	public String getContentType() {
		return contentType;
	}

	public String getContentEncoding() {
		return contentEncoding;
	}

	public int getContentLength() {
		return contentLength;
	}

	public byte[] getContent() {
		return content;
	}
	
	public void clear() {
		method = null;
		uri = null;
		httpVersion = null;
		
		query = new HashMap<>(DEFAULT_HEADER_SIZE);
		headers = new HashMap<>(DEFAULT_HEADER_SIZE);
		contentType = null;
		contentEncoding = null;
		contentLength = -1;
		
		content = null;
		pos = 0;
		finished = false;
		isChunked = false;
		remainBody.clear();
		chunkedBody.clear();
	}

	protected void setMethod(String method) throws IOException {
		if(!GET.equals(method) &&
			!POST.equals(method) &&
			!PUT.equals(method) &&
			!DELETE.equals(method) &&
			!OPTION.equals(method)) {
			throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED);
		}
		this.method = method;
	}

	protected void setUri(String uri) {
		uri = uri.trim();
		try {
			uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), "decode url '" + uri + "' failed");
		}
		int K_S = 1, V_S = 2;
		int state = 0, t = 0;
		char[] uriChars = uri.toCharArray();
		String key = null, theUri = null;
		for(int i = 0; i < uriChars.length; i++) {
			if(state == 0 && uriChars[i] == QUM) {
				theUri = new String(Arrays.copyOf(uriChars, i));
				state = K_S;
				t = i+1;
				continue;
			}
			if(state == K_S) {
				boolean ok = false;
				for(;i < uriChars.length; i++) {
					if(uriChars[i] == EQ) {
						ok = true;
						break ;
					}
				}
				if(ok) {
					key = new String(Arrays.copyOfRange(uriChars, t, i));
					state = V_S;
					t = i+1;
					continue;
				} else {
					throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), "invalid url '" + uri + "'");
				}
			}
			if(state == V_S) {
				boolean ok = false;
				for(;i < uriChars.length; i++) {
					if(uriChars[i] == AND) {
						ok = true;
						break ;
					}
				}
				if(ok || i == uriChars.length) {
					if(key == null) {
						throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), "invalid url '" + uri + "'");
					}
					query.put(key.trim(), new String(Arrays.copyOfRange(uriChars, t, i)).trim());
					key = null;
					state = K_S;
					t = i+1;
					continue;
				} else {
					throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), "invalid url '" + uri + "'");
				}
			}
		}
		if(theUri == null) {
			theUri = uri;
		}
		if(theUri.length() == 0 || theUri.charAt(0) != URI_SEP) {
			theUri = URI_SEP + theUri;
		}
		this.uri = theUri;
	}
	
	protected void setHttpVersion(String version) throws IOException {
		version = version.trim();
		for(int i = 0; i < VERSION.length; i++) {
			if(version.charAt(i) != VERSION[i]) {
				throw new HttpException(HttpStatus.HTTP_VERSION_NOT_SUPPORTED);
			}
		}
		this.httpVersion = version;
	}
	
	protected void parse(byte[] msg) throws IOException {
		if(msg.length <= HttpRequest.OPTION.length()) {
			throw new HttpException(HttpStatus.BAD_REQUEST);
		}
		contentLock.lock();
		try {
			int i = 0, p = 0;
			while(msg[i] != SPACE && i < HttpRequest.METHOD_LEN) {
				i++;
			}
			if(i >= HttpRequest.METHOD_LEN) {
				throw new HttpException(HttpStatus.BAD_REQUEST);
			}
			setMethod(new String(Arrays.copyOfRange(msg, p, i)));
			i++;
			p = i;
			while(msg[i] != SPACE) {
				if(msg[i] == ENTER || i == msg.length - 1) {
					throw new HttpException(HttpStatus.BAD_REQUEST);
				}
				i++;
			}
			setUri(new String(Arrays.copyOfRange(msg, p, i)));
			i++;
			p = i;
			while(msg[i] != ENTER) {
				if(i == msg.length - 1) {
					throw new HttpException(HttpStatus.BAD_REQUEST);
				}
				i++;
			}
			setHttpVersion(new String(Arrays.copyOfRange(msg, p, i)));
			i++;
			p = i;
			int state = KEY_START;
			String key = null, val = null;
	    	for(; i < msg.length; i++) {
	    		if(state == KEY_START && msg[i] == ENTER) {
	    			break;
	    		}
	    		if(state == KEY_START && msg[i] == COLON) {
	    			key = new String(Arrays.copyOfRange(msg, p, i)).trim();
	    			p = i + 1;
	    			state = VAL_START;
	    			continue;
	    		}
	    		if(state == VAL_START && msg[i] == ENTER) {
	    			if(key == null) {
	    				throw new HttpException(HttpStatus.BAD_REQUEST);
	    			}
	    			val = new String(Arrays.copyOfRange(msg, p, i)).trim();
	    			headers.put(key.toLowerCase(), val);
	    			p = i + 1;
	    			state = KEY_START;
	    		}
	    	}
			this.contentType = headers.getOrDefault(HEADER_CONTENT_TYPE, null);
			this.contentEncoding = headers.getOrDefault(HEADER_CONTENT_ENCODE, null);
			if(contentType != null && contentEncoding == null) {
				int sem;
				if((sem = contentType.indexOf(SEM)) > 0) {
					contentEncoding = contentType.substring(sem + 1).trim();
					if(!contentEncoding.startsWith(DEFAULT_ENCODING_KEY)) {
						contentEncoding = DEFAULT_ENCODING_VAL;
					} else {
						contentEncoding = contentEncoding
								.substring(DEFAULT_ENCODING_KEY.length() + 1).trim();
					}
					contentType = contentType.substring(0, sem).trim();
				}
			}
			while(i < msg.length && msg[i] != ENTER) {
				i++;
			}
			if(i < msg.length) {
				i++;
			}
			String contentLengthStr = headers.getOrDefault(HEADER_CONTENT_LENGTH, null);
			String transferEncoding = headers.getOrDefault(HEADER_TRANSFER_ENCODING, null);
			if(contentLengthStr != null) {
				try {
					contentLength = Integer.parseInt(contentLengthStr);
				} catch(Exception e) {
					throw new HttpException(HttpStatus.BAD_REQUEST);
				}
				if(contentLength > 0) {
					if(msg.length - i > contentLength) {
						System.err.println("content remaining " + (msg.length - i) + 
								"while content length is " + contentLengthStr);
						throw new HttpException(HttpStatus.BAD_REQUEST);
					}
					if(i > msg.length) {
						throw new HttpException(HttpStatus.BAD_REQUEST);
					}
					content = new byte[contentLength];
					if(i < msg.length) {
						System.arraycopy(msg, i, content, 0, msg.length - i);
						pos += msg.length - i;
					}
				} else if(contentLength == 0) {
					content = new byte[0];
				} else {
					throw new HttpException(HttpStatus.BAD_REQUEST);
				}
				if(pos == contentLength) {
					finished = true;
				}
			} else if(transferEncoding != null && CHUNKED.equals(transferEncoding)) {
				isChunked = true;
				int s = i, len = 0;
				state = CHUNKED_LEN;
				for(; i < msg.length; i++) {
		    		if(state == CHUNKED_LEN && msg[i] == ENTER) {
		    			len = HexUtil.bytesToInt(msg, s, i - 1);
		    			state = CHUNKED_VAL;
		    			if(len == 0) {
		    				finished = true;
		    				content = chunkedBody.readAll();
		    				break;
		    			} else {
		    				if(len + i + 1 > msg.length) {
		    					remainBody.write(msg, s, msg.length - s);
		    					break;
		    				} else {
		    					chunkedBody.write(msg, i + 1, len);
		    				}
		    				i += 1 + len;
		    			}
		    			continue;
		    		}
		    		if(state == CHUNKED_VAL && msg[i] == ENTER) {
		    			s = i + 1;
		    			state = CHUNKED_LEN;
		    		}
				}
			} else {
				contentLength = 0;
				content = new byte[0];
				finished = true;
			}
			if(finished) {
				chunkedBody.clear();
				remainBody.clear();
			}
		} finally {
			contentLock.unlock();
		}
	}
	
	protected synchronized void putContent(byte[] content) {
		if(this.content == null) {
			throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
					"content is not expected here.");
		}
		contentLock.lock();
		try {
			if(isChunked) {
				int s = 0, len = 0, state = CHUNKED_LEN;
				if(remainBody.avaliable() > 0) {
					remainBody.write(content);
					content = remainBody.readAll();
				}
				for(int i = 0; i < content.length; i++) {
		    		if(state == CHUNKED_LEN && content[i] == ENTER) {
		    			len = HexUtil.bytesToInt(content, s, i - 1);
		    			state = CHUNKED_VAL;
		    			if(len == 0) {
		    				finished = true;
		    				break;
		    			} else {
		    				if(len + i + 1 > content.length) {
		    					remainBody.write(content, s, content.length - s);
		    					break;
		    				} else {
		    					chunkedBody.write(content, i + 1, len);
		    				}
		    				i += 1 + len;
		    			}
		    			continue;
		    		}
		    		if(state == CHUNKED_VAL && content[i] == ENTER) {
		    			s = i + 1;
		    			state = CHUNKED_LEN;
		    		}
				}
			} else {
				if(content.length + pos > this.content.length) {
					throw new HttpException(HttpStatus.BAD_REQUEST.getCode(),
							"content bytes over flow.");
				}
				pos += content.length;
				if(pos == this.contentLength) {
					finished = true;
				}
			}
		} finally {
			if(finished) {
				this.content = chunkedBody.readAll();
				chunkedBody.clear();
				remainBody.clear();
			}
			contentLock.unlock();
		}
	}
	
	protected boolean isFinished() {
		return finished;
	}
	
	protected boolean isEmpty() {
		return method == null || uri == null || httpVersion == null;
	}
}
