package com.archer.jdknet;

public class ChannelException extends RuntimeException {

    static final long serialVersionUID = -3321467993124229948L;
    
    static final int STACK_DEEPTH = 8;
    static final int STACK_LEN = 128;
    
    public ChannelException(Throwable e) {
    	super(e.getMessage());
    }
    
    public ChannelException(String msg) {
    	super(msg);
    }
    
    public static ChannelException formatException(Exception e) {
    	StackTraceElement[] stacks = e.getStackTrace();
    	String msg = e.getLocalizedMessage();
    	StringBuilder sb = new StringBuilder(msg.length() + STACK_DEEPTH * STACK_LEN);
    	sb.append(msg);
    	for(int i = 0; i < STACK_DEEPTH; i++) {
    		StackTraceElement el = stacks[i];
    		sb.append(';').append(el.getClassName()).append('.')
    		.append(el.getMethodName()).append('.').append(el.getLineNumber());
    	}
    	return new ChannelException(sb.toString());
    }
}
