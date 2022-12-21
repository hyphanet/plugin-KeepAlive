package keepalive.repository;

import freenet.keys.FreenetURI;

public interface IDatabaseBlock {
	
	FreenetURI getUri();
	
	void setUri(FreenetURI uri);
	
	byte[] getData();
	
	void setData(byte[] data);
	
}
