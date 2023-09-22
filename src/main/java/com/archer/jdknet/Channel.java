package com.archer.jdknet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class Channel {

	private static final int READ_BUF_SIZE = 1024*1024;
	private static final int WRITE_BUF_SIZE = 1024*1024;
	
    private static final int READ_TRY = 3;
	
    private String id;
    
	private String host;
	private Integer port;
	
    private ByteBuffer peerReadBuf;
	private ByteBuffer peerWriteBuf;
	
	private Bytes readBytes;
	private Bytes writeBytes;
	
	private SelectionKey key;
	private HandlerWorker worker;
	
	private ChannelState state;
	private boolean clientMode;
	
	private ClientChannel cli;
	
	private ReentrantLock readLock = new ReentrantLock(true);
	private ReentrantLock writeLock = new ReentrantLock(true);
	
	protected Channel(String host, Integer port, SelectionKey key, HandlerWorker worker) {
		this.id = UUID.randomUUID().toString().replace("-", "");
		this.host = host;
		this.port = port;
		this.key = key;
		this.worker = worker;
		this.peerReadBuf = ByteBuffer.allocateDirect(READ_BUF_SIZE);
		this.peerWriteBuf = ByteBuffer.allocateDirect(WRITE_BUF_SIZE);
		this.readBytes = new Bytes();
		this.writeBytes = new Bytes();
		this.state = ChannelState.OPEN;
		this.clientMode = false;
		peerReadBuf.flip();
		peerWriteBuf.flip();
	}
	
	protected SocketChannel socketChannel() {
		return (SocketChannel) key.channel();
	}
	
	protected SelectionKey key() {
		return key;
	}

	protected HandlerWorker worker() {
		return worker;
	}
	
	protected Bytes readBytes() {
		return readBytes;
	}
	
	protected void clientMode(boolean mode) {
		clientMode = mode;
	}
	
	protected void clientChannel(ClientChannel cli) {
		this.cli = cli;
	}
	
	protected ClientChannel clientChannel() {
		return cli;
	}
	
	public boolean isClientMode() {
		return clientMode;
	}
	
	public void prepareClose() {
		this.state = ChannelState.CLOSING;
	}
	
	public boolean isOpen() {
		return state == ChannelState.OPEN;
	}

	public void close() throws IOException {
		state = ChannelState.CLOSED;
		key.channel().close();
		key.cancel();
		if(clientMode && cli != null) {
			cli.close();
		}
	}
	
	public int read(Bytes in) throws IOException {
		if(in == null) {
			throw new NullPointerException();
		}
		readLock.lock();
		if(!isOpen()) {
			return 0;
		}
		if(readBytes.avaliable() <= 0) {
			int r = readInternal();
			if(r <= 0) {
				return r;
			}
		}
		int count = readBytes.writeToBytes(in);
		readLock.unlock();
		return count;
	}
	
	public void write(Bytes out) throws IOException {
		if(out.avaliable() <= 0) {
			return ;
		}
		writeLock.lock();
		if(!isOpen()) {
			return ;
		}
		writeBytes.readFromBytes(out);
		key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
		key.selector().wakeup();
		writeLock.unlock();
	}
	
	public String remoteHost() {
		return host;
	}

	public Integer remotePort() {
		return port;
	}
	
	public String getId() {
		return id;
	}
	
	protected int readInternal() throws IOException {
		int count = 0, readCount = 0;
		SocketChannel client = (SocketChannel) key.channel();

		readLock.lock();
		peerReadBuf.clear();
		while(count < READ_TRY && peerReadBuf.hasRemaining()) {
			int readBytes = client.read(peerReadBuf);
			if(readBytes > 0) {
				readCount += readBytes;
			} else if (readBytes == 0) {
				count++;
			} else {
				return -1;
			}
		}
		peerReadBuf.flip();
		if(peerReadBuf.hasRemaining()) {
			readBytes.readFromDirectBuffer(peerReadBuf);
		}
		readLock.unlock();
		
		return readCount;
	}
	
	protected void writeInternal() throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		
		writeLock.lock();
		while(writeBytes.avaliable() > 0) {
			writeBytes.writeToDirectBuffer(peerWriteBuf);
			peerWriteBuf.flip();
			while(peerWriteBuf.hasRemaining()) {
				client.write(peerWriteBuf);
			}
			peerWriteBuf.clear();
		}
		key.interestOps(SelectionKey.OP_READ);
		writeLock.unlock();
	}
	
	static enum ChannelState {
		OPEN,
		CLOSING,
		CLOSED
	}
}
