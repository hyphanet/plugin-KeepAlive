package keepalive.service.reinserter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import freenet.client.ClientMetadata;
import freenet.client.FetchException;
import freenet.client.FetchWaiter;
import freenet.client.InsertContext.CompatibilityMode;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetState;
import freenet.client.async.GetCompletionCallback;
import freenet.client.async.StreamGenerator;
import freenet.crypt.HashResult;
import freenet.support.compress.Compressor;
import keepalive.Plugin;

class SplitfileGetCompletionCallback implements GetCompletionCallback {
	
	private final FetchWaiter fetchWaiter;
	private byte[] decompressedSplitFileData = null;
	private final Plugin plugin;
	
	SplitfileGetCompletionCallback(FetchWaiter fetchWaiter, Plugin plugin) {
		this.fetchWaiter = fetchWaiter;
		this.plugin = plugin;
	}
	
	@Override
	public void onFailure(FetchException e, ClientGetState state, ClientContext context) {
		fetchWaiter.onFailure(e, null);
	}
	
	@Override
	public void onSuccess(StreamGenerator streamGenerator, ClientMetadata clientMetadata,
			List<? extends Compressor> decompressors,
			ClientGetState state, ClientContext context) {
		try {
			
			// get data
			final ByteArrayOutputStream rawOutStream = new ByteArrayOutputStream();
			streamGenerator.writeTo(rawOutStream, null);
			rawOutStream.close();
			final byte[] compressedSplitFileData = rawOutStream.toByteArray();
			
			// decompress (if necessary)
			if (decompressors.size() > 0) {
				try (ByteArrayInputStream compressedInStream = new ByteArrayInputStream(compressedSplitFileData);
						ByteArrayOutputStream decompressedOutStream = new ByteArrayOutputStream()) {
					decompressors.get(0).decompress(compressedInStream, decompressedOutStream, Integer.MAX_VALUE, -1);
					decompressedSplitFileData = decompressedOutStream.toByteArray();
				}
				fetchWaiter.onSuccess(null, null);
			} else {
				decompressedSplitFileData = compressedSplitFileData;
			}
			
		} catch (final IOException e) {
			plugin.log("SplitfileGetCompletionCallback.onSuccess(): " + e.getMessage());
		}
	}
	
	byte[] getDecompressedData() {
		return decompressedSplitFileData;
	}
	
	@Override
	public void onBlockSetFinished(ClientGetState state, ClientContext context) {}
	
	@Override
	public void onExpectedMIME(ClientMetadata metadata, ClientContext context) {}
	
	@Override
	public void onExpectedSize(long size, ClientContext context) {}
	
	@Override
	public void onFinalizedMetadata() {}
	
	@Override
	public void onTransition(ClientGetState oldState, ClientGetState newState, ClientContext context) {}
	
	@Override
	public void onExpectedTopSize(long size, long compressed, int blocksReq, int blocksTotal, ClientContext context) {}
	
	@Override
	public void onHashes(HashResult[] hashes, ClientContext context) {}
	
	@Override
	public void onSplitfileCompatibilityMode(CompatibilityMode min, CompatibilityMode max, byte[] customSplitfileKey, boolean compressed, boolean bottomLayer, boolean definitiveAnyway,
			ClientContext context) {}
	
}
