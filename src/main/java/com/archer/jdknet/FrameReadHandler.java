package com.archer.jdknet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class FrameReadHandler extends Handler {

	private static ConcurrentHashMap<Channel, FrameMessage> frameCache = new ConcurrentHashMap<>();
	
	private int off, len, headLen;
	
	public FrameReadHandler(int off, int len, int headLen) {
		this.off = off;
		this.len = len;
		this.headLen = headLen;
	}
	
	@Override
	public void onConnect(Channel channel) throws Exception {
		getFrameMessage(channel);
		toNextOnConnect(channel);
	}

	@Override
	public void onRead(Channel channel, Bytes in) throws Exception {
		FrameMessage frame = getFrameMessage(channel);
		Bytes read = frame.read(in, off, len, headLen);
		if(read != null) {
			toNextOnRead(channel, read);
		}
	}

	@Override
	public void onWrite(Channel channel, Bytes out) throws Exception {
		toLastOnWrite(channel, out);
	}

	@Override
	public void onDisconnect(Channel channel) throws Exception {
		frameCache.remove(channel);
		toNextOnDisconnect(channel);
	}

	@Override
	public void onError(Channel channel, Throwable t) {
		toNextOnError(channel, t);
	}

	@Override
	public boolean isFinalHandler() {
		return false;
	}
	
	private FrameMessage getFrameMessage(Channel channel) {
		FrameMessage msg = frameCache.getOrDefault(channel, null);
		if(msg == null) {
			msg = new FrameMessage();
			frameCache.put(channel, msg);
		}
		return msg;
	}
	
	private class FrameMessage {
		
        ReentrantLock frameLock = new ReentrantLock(true);
        
		byte[] data;
		int pos;
		
		public FrameMessage() {}
		
		public Bytes read(Bytes in, int off, int len, int headLen) {
			try {
				frameLock.lock();
                
				int readCount;
				if(data == null) {
					int dataLen = getFrameLength(in, off, len) + headLen;
					data = new byte[dataLen];
					pos = 0;
					readCount = dataLen > in.avaliable() ? in.avaliable() : dataLen;
				} else {
					int remain = data.length - pos;
					readCount = remain > in.avaliable() ? in.avaliable() : remain;
				}
				in.read(data, pos, readCount);
				pos += readCount;
				if(readCount >= data.length) {
					Bytes read =  new Bytes(data);
					data = null;
					pos = 0;
					return read;
				}
				return null;
			} finally {
				frameLock.unlock();
			}
		}
		
		private int getFrameLength(Bytes in, int off, int len) {
			if(len >= 4) {
				throw new IllegalArgumentException("length out of int's range");
			}
			int base = 8 * (len - 1), readLen = 0;
			for(int i = 0; i < len; i++) {
				int b = in.byteAt(off + i);
				b = b < 0 ? b + 256 : b;
				readLen |= (b << base);
				base -= 8;
			}
			return readLen;
		}
	}
}
