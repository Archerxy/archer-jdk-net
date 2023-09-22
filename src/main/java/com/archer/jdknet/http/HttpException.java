package com.archer.jdknet.http;

public class HttpException extends RuntimeException {

	private static final long serialVersionUID = 128378174983472L;

	private int code;
	

	public HttpException(HttpStatus status) {
		this(status.getCode(), status.getMsg());
	}
	
	public HttpException(int code, String msg) {
		super(msg);
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
