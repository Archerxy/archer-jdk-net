package com.archer.jdknet.http;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NioResponse {
	public static final int HTTP_OK = 200;

	static final char COLON = ':';
	static final char SPACE = ' ';
	static final char LF = '\n';
    static final char[] PROTOCOL = { 'H', 'T', 'T', 'P', '/', '1', '.', '1', ' ' };

	static final int STATUS_LEN = 3;
    static final int DEFAULT_HEADER_SIZE = 6;
    
    static final int KEY_START = 1;
    static final int VAL_START = 2;

    static final int CHUNKED_LEN = 3;
    static final int CHUNKED_VAL = 4;

    static final String CONTENT_LENGTH = "content-length";
    static final String TRANSFER_ENCODING = "transfer-encoding";
    static final String CHUNKED = "chunked";
    static final String ERR_MSG = "parse http response failed. ";
	
	int statusCode;
	
	String status;
	
	Map<String, String> headers;
	
	byte[] body;
	
	String responseString;

	private NioResponse(int statusCode, String status, 
			Map<String, String> headers, byte[] body) {
		this.statusCode = statusCode;
		this.status = status;
		this.headers = headers;
		this.body = body;
	}

	private NioResponse responseString(String responseString) {
		this.responseString = responseString;
		return this;
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

	public String getResponseString() {
		return responseString;
	}

	protected static NioResponse parseResponseBytes(byte[] res) throws IOException {
		String rawRes = new String(res);
		int i = 0, s;
    	for(; i < PROTOCOL.length; i++) {
    		if(PROTOCOL[i] != res[i]) {
    			throw new IOException(ERR_MSG + rawRes);
    		}
    	}
    	int statusCode;
    	String status = null;
    	byte[] body;
    	Map<String, String> headers = new HashMap<>(DEFAULT_HEADER_SIZE);
    	try {
    		statusCode = Integer.parseInt(new String(Arrays.copyOfRange(res, i, i + STATUS_LEN)));
    	} catch(Exception e) {
			throw new IOException(ERR_MSG + rawRes);
    	}
    	s = i;
    	for(; i < res.length; i++) {
    		if(res[i] == LF) {
    			status = new String(Arrays.copyOfRange(res, s, i)).trim();
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
    				throw new IOException(ERR_MSG + new String(res));
    			}
    			val = new String(Arrays.copyOfRange(res, s, i)).trim();
    			headers.put(key.toLowerCase(), val);
    			s = i + 1;
    			state = KEY_START;
    			continue;
    		}
    	}

		String contentLengthStr = headers.getOrDefault(CONTENT_LENGTH, null);
		String transferEncoding = headers.getOrDefault(TRANSFER_ENCODING, null);
		if(contentLengthStr != null) {
			int contentLength = Integer.parseInt(contentLengthStr);
			body = Arrays.copyOfRange(res, i, i + contentLength);
		} else if(transferEncoding != null && CHUNKED.equals(transferEncoding)) {
			byte[] tmpBody = new byte[res.length - i];
			s = i;
			state = CHUNKED_LEN;
			int len = 0, off = 0;
			for(; i < res.length; i++) {
	    		if(state == CHUNKED_LEN && res[i] == LF) {
	    			len = getLength(res, s, i);
	    			state = CHUNKED_VAL;
	    			if(len == 0) {
	    				break;
	    			} else {
		    			System.arraycopy(res, i + 1, tmpBody, off, len);
	    				off += len;
	    				i += 1 + len;
	    			}
	    			continue;
	    		}
	    		if(state == CHUNKED_VAL && res[i] == LF) {
	    			s = i + 1;
	    			state = CHUNKED_LEN;
	    		}
			}
			body = Arrays.copyOfRange(tmpBody, 0, off);
		} else {
			body = Arrays.copyOfRange(res, i, res.length);
		}
    	
		return new NioResponse(statusCode, status, headers, body)
				.responseString(rawRes);
	}
	
	private static int getLength(byte[] data, int from, int to) {
		int[] radix16 = new int['z' + 1];
		for(int i = '0'; i <= '9'; i++) {
			radix16[i] = i - '0' + 1;
		}
		for(int i = 'a'; i <= 'z'; i++) {
			radix16[i] = i - 'a' + 11;
		}
		for(int i = 'A'; i <= 'Z'; i++) {
			radix16[i] = i - 'A' + 11;
		}
		int ret = 0;
		for(int i = from; i < to; i++) {
			int v = radix16[data[i]];
			if(v > 0) {
				ret = ret * 16 + v - 1;
			}
		}
		return ret;
	}
}
