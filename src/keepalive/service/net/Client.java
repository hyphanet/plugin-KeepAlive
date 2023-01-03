package keepalive.service.net;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClientImpl;
import freenet.client.InsertBlock;
import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.support.io.ArrayBucket;

public class Client {
	
	private static RequestClient rc = new RequestClient() {
		
		@Override
		public boolean persistent() {
			return false;
		}
		
		@Override
		public boolean realTimeFlag() {
			return true;
		}
		
	};
	
	private Client() {}
	
	// fetch raw data
	public static FetchResult fetch(FreenetURI uri, HighLevelSimpleClientImpl hlsc) throws FetchException {
		uri = normalizeUri(uri);
		if (uri == null)
			return null;
		
		if (uri.isCHK()) {
			uri.getExtra()[2] = 0; // deactivate control flag
		}
		
		final FetchContext fetchContext = hlsc.getFetchContext();
		fetchContext.returnZIPManifests = true;
		final FetchWaiter fetchWaiter = new FetchWaiter(rc);
		hlsc.fetch(uri, Long.MAX_VALUE, fetchWaiter, fetchContext); // TODO/FIXME: after Fred update (>1495) Long.MAX_VALUE to -1 again
		return fetchWaiter.waitForCompletion();
	}
	
	public static FreenetURI insert(FreenetURI uri, byte[] data, HighLevelSimpleClientImpl hlsc) throws InsertException {
		final InsertBlock insert = new InsertBlock(new ArrayBucket(data), null, uri);
		return hlsc.insert(insert, false, null);
	}
	
	public static FreenetURI normalizeUri(FreenetURI uri) {
		if (uri.isUSK()) {
			uri = uri.sskForUSK();
		}
		if (uri.hasMetaStrings()) {
			uri = uri.setMetaString(null);
		}
		return uri;
	}
	
}
