package com.archer.jdknet.p2p;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

final class CertificateChecker extends X509ExtendedTrustManager {

	private X509Certificate ca;
	
	public CertificateChecker(X509Certificate ca) {
		this.ca = ca;
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
		checkCerts(chain);
	}
	
	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
		checkCerts(chain);
	}
	
	private void checkCerts(X509Certificate[] chain) throws CertificateException {
		if(chain.length <= 0) {
    		throw new CertificateException("can not found any valid certificate.");
    	}
    	X509Certificate remoteCrt = chain[0];
    	try {
			remoteCrt.verify(ca.getPublicKey());
		} catch (Exception e) {
			throw new CertificateException("certificate verify failed.");
		}
	}
	
	/**
	 * ignore methods below
	 * */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    @Override
    public X509Certificate[] getAcceptedIssuers() {return null;}@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
	
}
