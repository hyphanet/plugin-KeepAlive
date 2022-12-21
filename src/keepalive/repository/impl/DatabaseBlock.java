package keepalive.repository.impl;

import java.util.Arrays;
import java.util.Objects;

import freenet.keys.FreenetURI;
import keepalive.repository.IDatabaseBlock;

public class DatabaseBlock implements IDatabaseBlock {
	
	private FreenetURI uri;
	private byte[] data;
	
	public DatabaseBlock(FreenetURI uri, byte[] data) {
		this.uri = uri;
		this.data = data;
	}
	
	@Override
	public FreenetURI getUri() {
		return uri;
	}
	
	@Override
	public void setUri(FreenetURI uri) {
		this.uri = uri;
	}
	
	@Override
	public byte[] getData() {
		return data;
	}
	
	@Override
	public void setData(byte[] data) {
		this.data = data;
	}
	
	@Override
	public String toString() {
		return String.format("DatabaseBlock [uri=%s, data=%s]", uri, Arrays.toString(data));
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(data);
		return prime * result + Objects.hash(uri);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		final DatabaseBlock other = (DatabaseBlock) obj;
		return Arrays.equals(data, other.data) && Objects.equals(uri, other.uri);
	}
	
}
