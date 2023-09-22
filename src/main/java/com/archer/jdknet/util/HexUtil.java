package com.archer.jdknet.util;

public class HexUtil {
	private static int[] radix16 = new int['z' + 1];
	
	static {
		for(int i = '0'; i <= '9'; i++) {
			radix16[i] = i - '0' + 1;
		}
		for(int i = 'a'; i <= 'z'; i++) {
			radix16[i] = i - 'a' + 11;
		}
		for(int i = 'A'; i <= 'Z'; i++) {
			radix16[i] = i - 'A' + 11;
		}
	}
	
	
	public static int bytesToInt(byte[] data, int from, int to) {
		int ret = 0;
		for(int i = from; i < to; i++) {
			int v = radix16[data[i]];
			if(v > 0) {
				ret = ret * 16 + v - 1;
			}
		}
		return ret;
	}
}
