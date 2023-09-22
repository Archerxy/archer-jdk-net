package com.archer.jdknet;

public class HandlerException extends RuntimeException {

    static final long serialVersionUID = -33217993124229948L;
    
    public HandlerException(Throwable e) {
    	super(e.getMessage());
    }
    
    public HandlerException(String msg) {
    	super(msg);
    }
}
