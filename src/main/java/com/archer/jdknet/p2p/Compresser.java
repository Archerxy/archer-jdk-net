package com.archer.jdknet.p2p;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class Compresser {
	
	private static final int LEVEL = 5;
	private static final int BUF_SIZE = 1024 * 1024;
	
	public static byte[] compress(byte[] input) {
		Deflater compresser = new Deflater(LEVEL);
		compresser.setInput(input);
		compresser.finish();
		int offset = 0;
		byte[] buf = new byte[BUF_SIZE];
		while(!compresser.finished()) {
			offset += compresser.deflate(buf, offset, buf.length - offset);
			if(offset >= buf.length) {
				byte[] newBuf = new byte[buf.length << 1];
				System.arraycopy(buf, 0, newBuf, 0, buf.length);
				buf = newBuf;
			}
		}
		compresser.end();
		byte[] output = new byte[offset];
		System.arraycopy(buf, 0, output, 0, offset);
		return output;
	}
	

	public static byte[] decompress(byte[] input) throws DataFormatException {
		Inflater decompresser = new Inflater();
		decompresser.setInput(input);
		int offset = 0;
		byte[] buf = new byte[BUF_SIZE];
		while(!decompresser.finished()) {
			offset += decompresser.inflate(buf, offset, buf.length - offset);
			if(offset >= buf.length) {
				byte[] newBuf = new byte[buf.length << 1];
				System.arraycopy(buf, 0, newBuf, 0, buf.length);
				buf = newBuf;
			}
		}
		decompresser.end();
		byte[] output = new byte[offset];
		System.arraycopy(buf, 0, output, 0, offset);
		return output;
	}
	
}
