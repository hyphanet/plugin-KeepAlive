package keepalive.urivalues.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import freenet.keys.FreenetURI;
import keepalive.model.IBlock;
import keepalive.urivalues.IUriValue;
import keepalive.urivalues.IUriValuesDAO;

/**
 * Implementation of {@link IUriValue}<br>
 * This is the default implementation for an entity that is managed be the {@link IUriValuesDAO}
 */
public class UriValue implements IUriValue {
	
	private final int uriId;
	
	/** the freenet uri */
	private FreenetURI uri = null;
	/** block count or -1 if its need to be fetched */
	private int blockCount = -1;
	/** TODO */
	private Map<FreenetURI, IBlock> blocks = new HashMap<>();
	/** TODO */
	private String successSegments = null;
	/** TODO */
	private String success = "";
	/** 'MM.yyyy' formated date values values comma separated */
	private String history = "";
	/** TODO */
	private int segment = -1;
	
	public UriValue(int uriId) {
		this.uriId = uriId;
	}
	
	public UriValue(int uriId, FreenetURI uri) {
		this(uriId);
		this.uri = uri;
	}
	
	@Override
	public int getUriId() {
		return uriId;
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
	public void setBlockCount(int blockCount) {
		this.blockCount = blockCount;
	}
	
	@Override
	public int getBlockCount() {
		return blockCount;
	}
	
	@Override
	public Map<FreenetURI, IBlock> getBlocks() {
		return blocks;
	}
	
	@Override
	public void setBlocks(Map<FreenetURI, IBlock> blocks) {
		this.blocks = blocks;
	}
	
	@Override
	public String getSuccessSegments() {
		return successSegments;
	}
	
	@Override
	public void setSuccessSegments(String successSegments) {
		this.successSegments = successSegments;
	}
	
	@Override
	public String getSuccess() {
		return success;
	}
	
	@Override
	public void setSuccess(String success) {
		this.success = success;
	}
	
	@Override
	public String getHistory() {
		return history;
	}
	
	@Override
	public void setHistory(String history) {
		this.history = history;
	}
	
	@Override
	public int getSegment() {
		return segment;
	}
	
	@Override
	public void setSegment(int segment) {
		this.segment = segment;
	}
	
	@Override
	public String toString() {
		return String.format("UriValue [uriId=%s, uri=%s, blockCount=%s, blocks=%s, successSegments=%s, success=%s, history=%s, segment=%s]", uriId, uri, blockCount, blocks, successSegments, success,
				history, segment);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(blockCount, blocks, history, segment, success, successSegments, uri, uriId);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		final UriValue other = (UriValue) obj;
		return blockCount == other.blockCount && Objects.equals(blocks, other.blocks) && Objects.equals(history, other.history) && segment == other.segment && Objects.equals(success, other.success)
				&& Objects.equals(successSegments, other.successSegments) && Objects.equals(uri, other.uri) && uriId == other.uriId;
	}
	
}
