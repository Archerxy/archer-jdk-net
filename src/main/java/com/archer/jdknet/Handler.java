package com.archer.jdknet;

public abstract class Handler {
	
	protected Handler last;
	protected Handler next;

	protected Handler last(Handler last) {
		this.last = last;
		return this;
	}
	
	protected Handler next(Handler next) {
		this.next = next;
		return this;
	}
	
	protected void toNextOnConnect(Channel channel) throws Exception {
		if(next != null) {
			next.onConnect(channel);
		}
	}

	protected void toNextOnDisconnect(Channel channel) throws Exception {
		if(next != null) {
			next.onDisconnect(channel);
		}
	}
	
	protected void toNextOnRead(Channel channel, Bytes in) throws Exception {
		if(next != null) {
			next.onRead(channel, in);
		}
	}
	
	protected void toLastOnWrite(Channel channel, Bytes out) throws Exception {
		if(last != null) {
			last.onWrite(channel, out);
		} else {
			if(channel.isOpen()) {
				channel.write(out);
			}
		}
	}

	protected void toNextOnError(Channel channel, Throwable t) {
		if(next != null) {
			next.onError(channel, t);
		} else {
			System.err.println("the last handler did not handle the exception.");
			t.printStackTrace();
		}
	}
	
	public abstract void onConnect(Channel channel) throws Exception;
	
	public abstract void onRead(Channel channel, Bytes in) throws Exception;

	public abstract void onWrite(Channel channel, Bytes out) throws Exception;
	
	public abstract void onDisconnect(Channel channel) throws Exception;
	
	public abstract void onError(Channel channel, Throwable t);
	
	public abstract boolean isFinalHandler();
}
