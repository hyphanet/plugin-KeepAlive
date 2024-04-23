package keepalive.repository;

import freenet.keys.FreenetURI;

public interface IDatabaseBlock {
	
	int MAX_SIZE = 32768;
	
	FreenetURI getUri();
	
	void setUri(FreenetURI uri);
	
	byte[] getData();
	
	void setData(byte[] data);
	
}
