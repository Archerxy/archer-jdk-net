package com.archer.jdknet;

public abstract class SimpleHandler<I> extends Handler {

	@Override
	public void onRead(Channel channel, Bytes in) throws Exception {
		onMessage(channel, decode(in));
	}

	@Override
	public void onWrite(Channel channel, Bytes out) throws Exception {
		toLastOnWrite(channel, out);
	}

	@Override
	public boolean isFinalHandler() {
		return true;
	}
	
	public void write(Channel channel, I out) throws Exception {
		onWrite(channel, encode(out));
	}
	
	public abstract void onMessage(Channel channel, I input);
	
	public abstract I decode(Bytes in);
	
	public abstract Bytes encode(I output);
}
