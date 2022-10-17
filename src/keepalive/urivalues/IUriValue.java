package keepalive.urivalues;

import java.net.MalformedURLException;
import java.util.Map;

import freenet.keys.FreenetURI;
import keepalive.model.IBlock;

public interface IUriValue {
	
	int getUriId();
	
	FreenetURI getUri();
	
	void setUri(FreenetURI uri);
	
	String getUriString();
	
	void setUriString(String uri) throws MalformedURLException;
	
	String getShortUri();
	
	void setShortUri(String shortUri);
	
	int getBlockCount();
	
	void setBlockCount(int blocks);
	
	Map<FreenetURI, IBlock> getBlocks();
	
	void setBlocks(Map<FreenetURI, IBlock> blocks);
	
	String getSuccessSegments();
	
	void setSuccessSegments(String successSegments);
	
	String getSuccess();
	
	void setSuccess(String success);
	
	String getHistory();
	
	void setHistory(String history);
	
	int getSegment();
	
	void setSegment(int segment);
	
}
