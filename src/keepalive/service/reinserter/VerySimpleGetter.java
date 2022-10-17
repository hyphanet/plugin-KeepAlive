package keepalive.service.reinserter;

import freenet.client.async.ClientBaseCallback;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetState;
import freenet.client.async.ClientRequestSchedulerGroup;
import freenet.client.async.ClientRequester;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;

class VerySimpleGetter extends ClientRequester {
	
	private static final long serialVersionUID = -4860494764560738604L;
	private final FreenetURI uri;
	
	VerySimpleGetter(short priorityClass, FreenetURI uri, RequestClient rc) {
		super(priorityClass, rc);
		this.uri = uri;
	}
	
	@Override
	public ClientRequestSchedulerGroup getSchedulerGroup() {
		return null;
	}
	
	@Override
	public FreenetURI getURI() {
		return uri;
	}
	
	@Override
	public boolean isFinished() {
		return false;
	}
	
	@Override
	public void onTransition(ClientGetState cgs, ClientGetState cgs1, ClientContext context) {}
	
	@Override
	public void cancel(ClientContext cc) {}
	
	@Override
	public void innerNotifyClients(ClientContext cc) {}
	
	@Override
	protected void innerToNetwork(ClientContext cc) {}
	
	@Override
	protected ClientBaseCallback getCallback() {
		return null;
	}
	
}
