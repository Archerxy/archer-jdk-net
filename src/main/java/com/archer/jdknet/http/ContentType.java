package com.archer.jdknet.http;

public enum ContentType {

	/**
	 * popular content-type
	 * */
	APPLICATION_JSON("application/json"),
	APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded"),
	APPLICATION_OCTET_STREAM("application/octet-stream"),
	APPLICATION_XHTML("application/xhtml+xml"),
	APPLICATION_XML("application/xml"),
	APPLICATION_ATOM("application/atom-xml"),
	APPLICATION_MSWORD("application/msword"),
	

	TEXT_HTML("text/html"),
	TEXT_PLAIN("text/plain"),
	TEXT_XML("text/xml"),
	

	IMAGE_GIF("image/gif"),
	IMAGE_JPEG("image/jpeg"),
	IMAGE_JPG("image/jpg"),
	IMAGE_PNG("image/png"),
	IMAGE_ICO("image/xml"),
	IMAGE_SVG("image/svg"),

	MULTIPART_FORMDATA("multipart/form-data");
	
	private String name;
	
	ContentType(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
}
