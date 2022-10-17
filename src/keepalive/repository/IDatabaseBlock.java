package keepalive.repository;

public interface IDatabaseBlock {
	
	String getUri();
	
	void setUri(String uri);
	
	byte[] getData();
	
	void setData(byte[] data);
	
}
