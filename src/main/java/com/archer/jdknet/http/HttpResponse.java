package com.archer.jdknet.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class HttpResponse {

    private static final char COLON = ':';
    private static final char SPACE = ' ';
    private static final char SEM = ';';
    private static final char EQL = '=';
    private static final String ENTER = "\r\n";

    private static final String DEFAULT_VSERSION = "HTTP/1.1";
    private static final String HEADER_DATE = "Date";
    private static final String[] HEADER_KEY = {"Server", "Connection"};
	private static final String[] HEADER_VAL = 
		{"Java/"+System.getProperty("java.version"), "close"};
	private static final String HEADER_CONTENT_TYPE = "Content-type";
    private static final String HEADER_CONTENT_LENGTH = "Content-length";

    private static final String DEFAULT_ENCODING_VAL = "utf-8";
    private static final String DEFAULT_ENCODING_KEY = "charset";
    
    private static final int DEFAULT_HEADER_SIZE = 32;
    private static final int BASE_HEADER_LEN = 48;
	
    private String remoteHost;
    private int remotePort;
    
	private String version;
	private String status;

	private int statusCode;
	
	private String contentType;
	private String contentEncoding;
	private int contentLength;
	
	private Map<String, String> headers;
	
	private byte[] content;
	
	protected HttpResponse(String host, int port) {
		clear();
		remoteHost = host;
		remotePort = port; 
		headers = new HashMap<>(DEFAULT_HEADER_SIZE);
		for(int i = 0; i < HEADER_KEY.length; i++) {
			String key = HEADER_KEY[i], val = HEADER_VAL[i];
			headers.put(key, val);
		}
	}
	
	public String remoteHost() {
		return remoteHost;
	}

	public int remotePort() {
		return remotePort;
	}

	public int getStatusCode() {
		return statusCode;
	}
	
	public String getStatus() {
		return status;
	}

	public void setStatus(HttpStatus status) {
		this.statusCode = status.getCode();
		this.status = status.getStatus();
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(ContentType contentType) {
		this.contentType = contentType.getName();
		headers.put(HEADER_CONTENT_TYPE, this.contentType);
	}

	public String getContentEncoding() {
		if(contentEncoding == null) {
			return DEFAULT_ENCODING_VAL;
		} else {
			return contentEncoding;
		}
	}

	public void setContentEncoding(String contentEncoding) 
			throws IOException {
		try {
			new String(new byte[] {0}, contentEncoding);
		} catch (UnsupportedEncodingException e) {
			throw new IOException("undefined encoding " + 
					contentEncoding);
		}
		this.contentEncoding = contentEncoding;
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}
	
	public void setHeader(String k, String v) {
		if(k != null) {
			headers.put(k, v);
		}
	}
	
	public String getHeader(String k) {
		if(k != null) {
			return headers.getOrDefault(k, null);
		}
		return null;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		if(content != null) {
			this.contentLength = content.length;
		} else {
			this.contentLength = 0;
		}
		this.content = content;
	}

	protected void setVersion(String version) {
		this.version = version;
	}
	
	public void clear() {
		version = null;
		status = null;
		statusCode = 0;
		
		headers = new HashMap<>(DEFAULT_HEADER_SIZE);
		contentType = null;
		contentLength = -1;
		
		content = null;
	}
	
	public byte[] toBytes() throws IOException {
		StringBuilder sb = new StringBuilder(DEFAULT_HEADER_SIZE * BASE_HEADER_LEN);
		String localVersion = version;
		if(localVersion == null) {
			localVersion = DEFAULT_VSERSION;
		}
		sb.append(localVersion).append(SPACE).append(status).append(ENTER);
		
		String encode = getContentEncoding();
		HashSet<String> keySet = new HashSet<>();
		if(headers != null) {
			for(Map.Entry<String, String> header: headers.entrySet()) {
				keySet.add(header.getKey().toLowerCase());
				if(HEADER_CONTENT_LENGTH.toLowerCase()
						.equals(header.getKey().toLowerCase())) {
					continue ;
				}
				if(HEADER_CONTENT_TYPE.toLowerCase()
						.equals(header.getKey().toLowerCase())) {
					
					sb.append(header.getKey()).append(COLON).append(SPACE)
					.append(header.getValue()).append(SEM).append(SPACE)
					.append(DEFAULT_ENCODING_KEY).append(EQL)
					.append(encode).append(ENTER);
				} else {
					sb.append(header.getKey()).append(COLON).append(SPACE)
					.append(header.getValue()).append(ENTER);
				}
			}
		}
		if(!keySet.contains(HEADER_DATE.toLowerCase())) {
			sb.append(HEADER_DATE).append(COLON).append(SPACE)
			.append(LocalDateTime.now().toString())
			.append(ENTER);
		}
		if(!keySet.contains(HEADER_CONTENT_TYPE.toLowerCase())) {
			sb.append(HEADER_CONTENT_TYPE).append(COLON).append(SPACE)
			.append(ContentType.TEXT_HTML.getName()).append(SEM).append(SPACE)
			.append(DEFAULT_ENCODING_VAL).append(EQL)
			.append(encode).append(ENTER);
		}
		int contentLength = content != null ? content.length : 0;
		sb.append(HEADER_CONTENT_LENGTH).append(COLON).append(SPACE)
		.append(contentLength).append(ENTER);
		
		sb.append(ENTER);
		
		byte[] headerBytes;
		try {
			headerBytes = sb.toString().getBytes(encode);
		} catch (UnsupportedEncodingException e) {
			throw new IOException("undefined encoding " + encode);
		}
		byte[] resBytes;
		if(content != null && content.length > 0) {
			resBytes = new byte[headerBytes.length + content.length];
			System.arraycopy(headerBytes, 0, resBytes, 0, headerBytes.length);
			System.arraycopy(content, 0, resBytes, headerBytes.length, content.length);
		} else {
			resBytes = headerBytes;
		}
		return resBytes;
	}
}
