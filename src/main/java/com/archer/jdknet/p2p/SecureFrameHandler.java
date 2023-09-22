package com.archer.jdknet.p2p;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DataFormatException;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.Channel;
import com.archer.jdknet.ChannelException;
import com.archer.jdknet.Handler;
import com.archer.jdknet.HandlerException;

public class SecureFrameHandler extends Handler {

	private static final int BITS = 1024;
	private static final int BYTES = BITS >> 3;
	
	private static final byte CLIENT_PK = 11;
	private static final byte SERVER_SK = 12;
	private static final byte APP_DATA = 13;

	private static final byte COMPRESS = 21;
	private static final byte UN_COMPRESS = 22;
	
	private static ConcurrentHashMap<Channel, FrameMessage> frameCache = new ConcurrentHashMap<>();
	
	@Override
	public void onConnect(Channel channel) throws Exception {
		if(channel.isClientMode()) {
			FrameMessage msg = new FrameMessage(true);
			frameCache.put(channel, msg);
			toLastOnWrite(channel, msg.clientPk());
		}
	}

	@Override
	public void onRead(Channel channel, Bytes in) throws Exception {
		byte type = (byte) in.readInt8();
		FrameMessage frame = getFrameMessage(channel);
		switch(type) {
		case CLIENT_PK: {
			toLastOnWrite(channel, frame.serverSk(in));
			break;
		}
		case SERVER_SK: {
			frame.clientSk(in);
			break ;
		}
		case APP_DATA: {
			Bytes read = frame.appDataUnwrap(in);
			if(read != null) {
				toNextOnRead(channel, read);
			}
			break ;
		}
		default : {
			throw new HandlerException("undefined secure frame channel content type " + type);
		}
		}
	}

	@Override
	public void onWrite(Channel channel, Bytes out) throws Exception {
		FrameMessage frame = getFrameMessage(channel);
		toLastOnWrite(channel, frame.appDataWrap(out));
	}

	@Override
	public void onDisconnect(Channel channel) throws Exception {
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
			msg = new FrameMessage(false);
			frameCache.put(channel, msg);
		}
		return msg;
	}
	
	private class FrameMessage {

		private ReentrantLock frameLock = new ReentrantLock(true);
        
		private BigInteger e, d, n;
		private byte[] sk = null;

		private byte[] data = null;
		private int pos = 0;
		private boolean compressed = false;
		
		public FrameMessage(boolean init) {
			if(init) {
				BigInteger[] edn = genEDN();
				e = edn[0];
				d = edn[1];
				n = edn[2];	
			}
		}
		
		private byte[] initSk() {
			Random r = new Random();
			sk = new byte[BYTES - 1];
			r.nextBytes(sk);
			if(sk[0] == 0) {
				sk[0] = (byte) r.nextInt(256);
			}
			BigInteger skNum = new BigInteger(1, sk);
			BigInteger enSk = encrypt(e, n, skNum);
			return formatBytes(enSk.toByteArray(), BYTES);
		}
		
		private BigInteger[] genEDN() {
			SecureRandom sr = new SecureRandom();
			BigInteger p1 = BigInteger.probablePrime((BITS >> 1) - 1, sr);
			BigInteger p2 = BigInteger.probablePrime((BITS >> 1) - 1, sr);
			BigInteger n = p1.multiply(p2);
			BigInteger fiN = p1.subtract(BigInteger.ONE).multiply(p2.subtract(BigInteger.ONE));
			BigInteger e = BigInteger.probablePrime(fiN.bitLength(), sr);
			while(e.compareTo(fiN) >= 0) {
				e = BigInteger.probablePrime(fiN.bitLength() - 1, sr);
			}
			BigInteger d = e.modInverse(fiN);
			
			return new BigInteger[] {e, d, n};
		}
		
		private BigInteger encrypt(BigInteger e, BigInteger n, BigInteger message) {
			return message.modPow(e, n);
		}

		private BigInteger decrypt(BigInteger d, BigInteger n, BigInteger cipher) {
			return cipher.modPow(d, n);
		}
		
		private byte[] formatBytes(byte[] in, int len) {
			if(in.length == len) {
				return in;
			} else if(in.length < len) {
				byte[] ret = new byte[len];
				System.arraycopy(in, 0, ret, len- in.length, in.length);
				return ret;
			} else {
				int i = 0;
				while(in.length - i > len) {
					i++;
				}
				return Arrays.copyOfRange(in, i, in.length);
			}
		}
		
		public Bytes appDataWrap(Bytes out) {
			byte[] text = out.readAll();
			int head = 1 + 1 + 4;
			Bytes cipher = new Bytes(head + text.length);
			cipher.writeInt8(APP_DATA);
			if(text.length > Bytes.BUFFER_SIZE) {
				cipher.writeInt8(COMPRESS);
				text = Compresser.compress(text);
			} else {
				cipher.writeInt8(UN_COMPRESS);
			}
			cipher.writeInt32(text.length);
			int j = 0;
			for(int i = 0; i < text.length; i++) {
				if(j >= sk.length) {
					j = 0;
				}
				text[i] = (byte) (text[i] ^ sk[j++]);
			}
			cipher.write(text);
			return cipher;
		}
		
		public Bytes appDataUnwrap(Bytes in) {
			try {
				frameLock.lock();
				int readCount;
				if(data == null) {
					byte compress = (byte) in.readInt8();
					int dataLen = in.readInt32();
					compressed = compress == COMPRESS;
					data = new byte[dataLen];
					pos = 0;
					readCount = dataLen > in.avaliable() ? in.avaliable() : dataLen;
				} else {
					int remain = data.length - pos;
					readCount = remain > in.avaliable() ? in.avaliable() : remain;
				}
				in.read(data, pos, readCount);
				pos += readCount;
				if(pos >= data.length) {
					int j = 0;
					for(int i = 0; i < data.length; i++) {
						if(j >= sk.length) {
							j = 0;
						}
						data[i] = (byte) (data[i] ^ sk[j++]);
					}
					if(compressed) {
						try {
							data = Compresser.decompress(data);
						} catch (DataFormatException e) {
							throw new ChannelException("decompress failed.");
						}
					}
					Bytes read =  new Bytes(data);
					data = null;
					pos = 0;
					compressed = false;
					return read;
				}
				return null;
			} finally {
				frameLock.unlock();
			}
		}
		
		public Bytes clientPk() {
			byte[] eNum = formatBytes(e.toByteArray(), BYTES);
			byte[] nNum = formatBytes(n.toByteArray(), BYTES);
			Bytes clientPk = new Bytes();
			clientPk.writeInt8(CLIENT_PK);
			clientPk.write(eNum);
			clientPk.write(nNum);
			return clientPk;
		}
		
		public Bytes serverSk(Bytes in) {
			e = new BigInteger(1, in.read(BYTES));
			n = new BigInteger(1, in.read(BYTES));
			Bytes serverSk = new Bytes();
			serverSk.writeInt8(SERVER_SK);
			serverSk.write(initSk());
			return serverSk;
		}
		
		public void clientSk(Bytes in) {
			BigInteger cipherNum = new BigInteger(1, in.read(BYTES));
			BigInteger skNum = decrypt(d, n, cipherNum);
			sk = formatBytes(skNum.toByteArray(), BYTES - 1);
		}
	}
}
