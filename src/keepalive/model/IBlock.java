package keepalive.model;

import freenet.keys.FreenetURI;
import freenet.support.io.ArrayBucket;

public interface IBlock {
	
	int getId();
	
	int getSegmentId();
	
	FreenetURI getUri();
	
	ArrayBucket getBucket();
	
	void setBucket(ArrayBucket bucket);
	
	boolean isDataBlock();
	
	boolean isFetchInProcess();
	
	void setFetchDone(boolean done);
	
	boolean isInsertDone();
	
	void setInsertDone(boolean done);
	
	boolean isInsertSuccessful();
	
	void setInsertSuccessful(boolean successful);
	
	boolean isFetchSuccessful();
	
	void setFetchSuccessful(boolean successful);
	
	String getResultLog();
	
	void setResultLog(String result);
	
	void appendResultLog(String result);
	
}
