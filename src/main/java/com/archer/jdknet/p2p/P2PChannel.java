package com.archer.jdknet.p2p;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;

import com.archer.jdknet.Bytes;
import com.archer.jdknet.ChannelException;
import com.archer.jdknet.ClientChannel;
import com.archer.jdknet.Handler;
import com.archer.jdknet.HandlerException;
import com.archer.jdknet.ServerChannel;
import com.archer.jdknet.SslHandler;

public class P2PChannel {
	
	private ServerChannel server;
	private Set<ClientChannel> connections;
	
	private boolean useSsl = false;
	private SSLContext context;
	
	
	public P2PChannel(int port) throws IOException {
		server = new ServerChannel();
		server.bind(port);
	}
	
	public P2PChannel peers(EndPoint... peers) throws IOException {
		connections = new LinkedHashSet<>();
		for(EndPoint peer: peers) {
			ClientChannel peerChannel = new ClientChannel(peer.host(), peer.port());
			peerChannel.initHandlerWorker(server.handlerWorker());
			connections.add(peerChannel);
		}
		return this;
	}
	
	public P2PChannel useSsl(InputStream caStream, InputStream keyStream, InputStream crtStream)
			throws Exception {
		if(server.handlerInitialized()) {
			throw new HandlerException("do initialze ssl before adding handlers.");
		}
		useSsl = true;
		context = SSLContextBuilder.build(caStream, keyStream, crtStream);
		
		return this;
	}
	
	public P2PChannel handlers(Handler... handlers) {
		if(useSsl) {
			server.add(new SslHandler(context));
		} else {
			server.add(new SecureFrameHandler());
		}
		server.add(handlers);
		return this;
	}
	
	
	public void start() throws IOException {
		try {
			server.start();
		} catch(Exception e) {
			server.stop();
			return ;
		}
		for(ClientChannel connection: connections) {
			try {
				connection.connect();
			} catch (IOException e) {
				System.err.println("connect to " + connection.endpoint() + 
						" failed." + ChannelException.formatException(e));
			}
		}
	}
	
	public void stop() throws IOException {
		server.stop();
		for(ClientChannel connection: connections) {
			connection.close();
		}
	}
	
	public void send(EndPoint peer, Bytes msg) throws Exception {
		for(ClientChannel connection: connections) {
			if(connection.endpoint().equals(peer.enpoint())) {
				connection.send(msg);
				return ;
			}
		}
		System.err.println("can not found peer connection " + peer.enpoint());
	}
	
	public void disconnect(EndPoint peer) throws Exception {
		ClientChannel theChannel = null;
		for(ClientChannel connection: connections) {
			if(connection.endpoint().equals(peer.enpoint())) {
				theChannel = connection;
				break;
			}
		}
		if(theChannel != null) {
			theChannel.close();
			connections.remove(theChannel);
		}
	}
}
