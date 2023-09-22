package com.archer.jdknet;

public class HandlerWorkerBuilder {
	
	Handler[] handlers;
	
	public HandlerWorkerBuilder() {}
	
	public HandlerWorkerBuilder handlers(Handler... handlers) {
		this.handlers = handlers;
		return this;
	}
	
	public HandlerWorker build() {
		HandlerWorker worker = HandlerWorker.INSTANCE;
		worker.add(handlers);
		return worker;
	}
}
