package com.archer.jdknet.http.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.http.HttpException;
import com.archer.jdknet.http.HttpStatus;
import com.archer.jdknet.util.HexUtil;

public class Response {

	public static final int HTTP_OK = 200;
	private static final int ERROR_LEN = 16;

	private static final char COLON = ':';
	private static final char LF = '\n';
    private static final char SEM = ';';
	private static final char[] PROTOCOL = { 'H', 'T', 'T', 'P', '/', '1', '.', '1', ' ' };

	private static final int STATUS_LEN = 3;
	private static final int DEFAULT_SIZE = 6;
    
	private static final int KEY_START = 1;
	private static final int VAL_START = 2;

	private static final int CHUNKED_LEN = 3;
	private static final int CHUNKED_VAL = 4;

	private static final String CONTENT_LENGTH = "content-length";
	private static final String TRANSFER_ENCODING = "transfer-encoding";
	private static final String CONTENT_TYPE = "content-type";
	private static final String CONTENT_ENCODE = "content-encoding";
	
	private static final String CHUNKED = "chunked";

    private static final String DEFAULT_ENCODING_VAL = "utf-8";
    private static final String DEFAULT_ENCODING_KEY = "charset";
    
    private static final String ERR_MSG = "parse http response failed. ";
	

    private ReentrantLock contentLock = new ReentrantLock(true);
    
    private Bytes chunkedBody = new Bytes();
    private Bytes remainBody = new Bytes();
    private volatile boolean isChunked = false;
    private volatile boolean finished = false;
    private volatile boolean headerParsed = false;
    
    private int statusCode;
    private String status;
    private Map<String, String> headers;
	private String contentType;
	private String contentEncoding;
	private int contentLength;
	
	private byte[] body;

	protected Response() {
		this.headers = new HashMap<>(DEFAULT_SIZE);
		this.contentLength = -1;
	}
	
	public int getStatusCode() {
		return statusCode;
	}
	
	public String getStatus() {
		return status;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public byte[] getBody() {
		return body;
	}
	
	public String getContentType() {
		return contentType;
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentEncoding(String contentEncoding) {
		this.contentEncoding = contentEncoding;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}

	protected boolean finished() {
		return finished;
	}
	
	protected boolean headerParsed() {
		return headerParsed;
	}
	
	protected void parseHead(byte[] res) {
		contentLock.lock();
		if(headerParsed) {
			parseContent(res);
		}
		try {
			int i = 0, s;
	    	for(; i < PROTOCOL.length; i++) {
	    		if(PROTOCOL[i] != res[i]) {
	    			throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
	    					ERR_MSG + errorMessage(i, res));
	    		}
	    	}
	    	try {
	    		this.statusCode = Integer.parseInt(new String(Arrays.copyOfRange(res, i, i + STATUS_LEN)));
	    	} catch(Exception e) {
				throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
						ERR_MSG + errorMessage(i, res));
	    	}
	    	s = i;
	    	for(; i < res.length; i++) {
	    		if(res[i] == LF) {
	    			this.status = new String(Arrays.copyOfRange(res, s, i)).trim();
	    			i++;
	    			break;
	    		}
	    	}
	    	s = i;
			int state = KEY_START;
	    	String key = null, val = null;
	    	for(; i < res.length; i++) {
	    		if(state == KEY_START && res[i] == LF) {
	    			i++;
	    			break;
	    		}
	    		if(state == KEY_START && res[i] == COLON) {
	    			key = new String(Arrays.copyOfRange(res, s, i)).trim();
	    			s = i + 1;
	    			state = VAL_START;
	    			continue;
	    		}
	    		if(state == VAL_START && res[i] == LF) {
	    			if(key == null) {
	        			throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
	        					ERR_MSG + errorMessage(i, res));
	    			}
	    			val = new String(Arrays.copyOfRange(res, s, i)).trim();
	    			headers.put(key.toLowerCase(), val);
	    			s = i + 1;
	    			state = KEY_START;
	    			continue;
	    		}
	    	}
	    	this.contentType = headers.getOrDefault(CONTENT_TYPE, null);
			this.contentEncoding = headers.getOrDefault(CONTENT_ENCODE, null);
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
			String contentLengthStr = headers.getOrDefault(CONTENT_LENGTH, null);
			String transferEncoding = headers.getOrDefault(TRANSFER_ENCODING, null);
			if(contentLengthStr != null) {
				this.contentLength = Integer.parseInt(contentLengthStr);
				if(i >= res.length) {
					return ;
				}
				if(this.contentLength == 0) {
					finished = true;
					return ;
				}
				int length = contentLength + i > res.length ? res.length - i : contentLength;
				chunkedBody.write(res, i, length);
				if(length >= contentLength) {
					finished = true;
				}
			} else if(transferEncoding != null && CHUNKED.equals(transferEncoding)) {
				if(i >= res.length) {
					return ;
				}
				s = i;
				state = CHUNKED_LEN;
				int len = 0;
				for(; i < res.length; i++) {
		    		if(state == CHUNKED_LEN && res[i] == LF) {
		    			len = HexUtil.bytesToInt(res, s, i);
		    			state = CHUNKED_VAL;
		    			if(len == 0) {
		    				finished = true;
		    				break;
		    			} else {
		    				if(len + i + 1 > res.length) {
		    					remainBody.write(res, s, res.length - s);
		    					break;
		    				} else {
		    					chunkedBody.write(res, i + 1, len);
		    				}
		    				i += 1 + len;
		    			}
		    			continue;
		    		}
		    		if(state == CHUNKED_VAL && res[i] == LF) {
		    			s = i + 1;
		    			state = CHUNKED_LEN;
		    		}
				}
			} else {
				chunkedBody.clear();
				contentLength = 0;
				finished = true;
			}
		} finally {
			if(finished) {
				body = chunkedBody.readAll();
			}
			headerParsed = true;
			contentLock.unlock();
		}
	}
	
	protected void parseContent(byte[] content) {
		contentLock.lock();
		try {
			if(isChunked) {
				int s = 0, len = 0, state = CHUNKED_LEN;
				if(remainBody.available() > 0) {
					remainBody.write(content);
					content = remainBody.readAll();
				}
				for(int i = 0; i < content.length; i++) {
		    		if(state == CHUNKED_LEN && content[i] == LF) {
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
		    		if(state == CHUNKED_VAL && content[i] == LF) {
		    			s = i + 1;
		    			state = CHUNKED_LEN;
		    		}
				}
			} else {
				if(content.length + chunkedBody.available() > this.contentLength) {
					throw new HttpException(HttpStatus.BAD_REQUEST.getCode(),
							"content bytes over flow.");
				}
				chunkedBody.write(content);
				if(chunkedBody.available() >= this.contentLength) {
					finished = true;
				}
			}
			if(finished) {
				body = chunkedBody.readAll();
				chunkedBody.clear();
				remainBody.clear();
			}
		} finally {
			contentLock.unlock();
		}
	}
	
	
	private String errorMessage(int i, byte[] res) {
		return new String(Arrays.copyOfRange(res, i, i + ERROR_LEN > res.length ? res.length : i + ERROR_LEN));
	}
}
