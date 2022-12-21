package keepalive.urivalues;

import java.util.Map;

import freenet.keys.FreenetURI;
import keepalive.model.IBlock;

public interface IUriValue {
	
	int getUriId();
	
	FreenetURI getUri();
	
	void setUri(FreenetURI uri);
	
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
	
	default String getShortUri() {
		FreenetURI uri = getUri();
		if (uri == null)
			return "";
		
		String strUri = uri.toString();
		return strUri.substring(0, 20) + "...." + strUri.substring(strUri.length() - 50);
	}
	
}
