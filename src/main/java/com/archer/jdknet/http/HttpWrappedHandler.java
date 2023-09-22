package com.archer.jdknet.http;

import java.util.concurrent.ConcurrentHashMap;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.Channel;
import com.archer.jdknet.Handler;


public abstract class HttpWrappedHandler extends Handler {
	
	private static ConcurrentHashMap<String, HttpContext> contextCache = new ConcurrentHashMap<>();
    
    public HttpWrappedHandler() {}

	@Override
	public void onConnect(Channel channel) throws Exception {
		HttpContext context = getHttpContext(channel, true);
		contextCache.put(channel.getId(), context);
	}

	@Override
	public void onRead(Channel channel, Bytes in) throws Exception {
		if(in.avaliable() <= 0) {
			return ;
		}
		
		HttpContext context = getHttpContext(channel, true);
		HttpRequest req = context.request;
		HttpResponse res = context.response;
		byte[] msg = in.readAll();
		if(req.isEmpty()) {
			try {
				req.parse(msg);
				res.setVersion(req.getHttpVersion());
			} catch(HttpException e) {
				res.setStatus(HttpStatus.valueOf(e.getCode()));
				onWrite(channel, new Bytes(res.toBytes()));
			}
		} else {
			req.putContent(msg);
		}
		if(req.isFinished()) {
			try {
				handle(req, res);
			} catch(Exception e) {
				handleException(req, res, e);
			}
			onWrite(channel, new Bytes(res.toBytes()));
		}
	}
	
	@Override
	public void onWrite(Channel channel, Bytes out) throws Exception {
		HttpContext context = getHttpContext(channel, false);
		if(context == null) {
			onError(channel, new HttpException(HttpStatus.INTERNAL_SERVER_ERROR));
		}
		HttpRequest req = context.request;
		HttpResponse res = context.response;
		
		req.clear();
		res.clear();
		toLastOnWrite(channel, out);
	}
	
	@Override
	public void onDisconnect(Channel channel) throws Exception {
		contextCache.remove(channel.getId());
	}

	@Override
	public void onError(Channel channel, Throwable t) {
		HttpContext context = getHttpContext(channel, true);
		HttpRequest req = context.request;
		HttpResponse res = context.response;
		handleException(req, res, t);
	}

	@Override
	public boolean isFinalHandler() {
		return true;
	}
	
	private HttpContext getHttpContext(Channel channel, boolean create) {
		HttpContext context = contextCache.getOrDefault(channel.getId(), null);
		if(create && context == null) {
			context = new HttpContext(channel.remoteHost(), channel.remotePort());
		}
		return context;
	}
	
	public abstract void handle(HttpRequest req, HttpResponse res) throws Exception;
	
	public abstract void handleException(HttpRequest req, HttpResponse res, Throwable t);
	
	
	private static class HttpContext {
		
		HttpRequest request;
		HttpResponse response;
		
		public HttpContext(String host, int port) {
			this.request = new HttpRequest(host, port);
			this.response = new HttpResponse(host, port);
		}
	}
}
