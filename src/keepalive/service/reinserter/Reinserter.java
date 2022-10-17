/*
 * Keep Alive Plugin
 * Copyright (C) 2012 Jeriadoc
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package keepalive.service.reinserter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import org.apache.tools.tar.TarInputStream;

import freenet.client.ArchiveManager.ARCHIVE_TYPE;
import freenet.client.FECCodec;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.InsertException;
import freenet.client.Metadata;
import freenet.client.Metadata.SplitfileAlgorithm;
import freenet.client.MetadataParseException;
import freenet.client.async.ClientContext;
import freenet.client.async.SplitFileFetcher;
import freenet.client.async.SplitFileSegmentKeys;
import freenet.keys.CHKBlock;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.compress.Compressor.COMPRESSOR_TYPE;
import freenet.support.io.ArrayBucket;
import keepalive.Plugin;
import keepalive.exceptions.DAOException;
import keepalive.exceptions.FetchFailedException;
import keepalive.model.Block;
import keepalive.model.IBlock;
import keepalive.model.PropertiesKey;
import keepalive.model.Segment;
import keepalive.repository.IDatabaseBlock;
import keepalive.service.net.Client;
import keepalive.service.net.SingleFetch;
import keepalive.service.net.SingleInsert;
import keepalive.service.net.SingleJob;
import keepalive.urivalues.IUriValue;

public final class Reinserter extends Thread {
	
	private final Plugin plugin;
	private final IUriValue uriValue;
	private final CountDownLatch latch;
	private PluginRespirator pr;
	private long lastActivityTime;
	private Map<FreenetURI, Metadata> manifestURIs;
	//private Map<FreenetURI, IBlock> blocks;
	private int parsedSegmentId;
	private int parsedBlockId;
	private final ArrayList<Segment> segments = new ArrayList<>();
	
	public Reinserter(Plugin plugin, IUriValue uriValue, CountDownLatch latch) {
		this.plugin = plugin;
		this.uriValue = uriValue;
		this.latch = latch;
		this.setName(Plugin.PLUGIN_NAME + " ReInserter " + uriValue.getUriId());
	}
	
	@Override
	public void run() {
		final int siteId = uriValue.getUriId();
		
		try {
			// init
			pr = plugin.pluginContext.pluginRespirator;
			manifestURIs = new HashMap<>();
			//blocks = new HashMap<>();
			final String rawUri = uriValue.getUriString();
			
			plugin.log("start reinserter for site " + rawUri + " (" + siteId + ")", 1);
			plugin.clearLog(plugin.getLogFilename(uriValue));
			isActive(true);
			long startedAt = System.currentTimeMillis();
			long timeLeft = TimeUnit.HOURS.toMillis(plugin.getIntProp(PropertiesKey.SINGLE_URL_TIMESLOT));
			
			FreenetURI uri = new FreenetURI(rawUri);
			
			// update if USK
			if (uri.isUSK()) {
				final FreenetURI newUri = updateUsk(uri);
				if (newUri != null && !newUri.equals(uri)) {
					plugin.log("received new uri: " + newUri, 1);
					if (plugin.uriPropsDAO.exist(newUri)) {
						plugin.log("remove uri as duplicate: " + newUri, 1);
						plugin.removeUriAndFiles(uriValue);
						return;
					}
					
					uriValue.setUri(newUri);
					uriValue.setBlockCount(-1);
					plugin.uriPropsDAO.update(uriValue);
					
					uri = newUri;
				}
			}
			
			// check top block availability
			final FreenetURI topBlockUri = Client.normalizeUri(uri.clone());
			if (plugin.databaseDAO.lastAccessDiff(topBlockUri.toString()) > TimeUnit.DAYS.toMillis(1)) {
				try {
					Client.fetch(topBlockUri, plugin.getFreenetClient());
				} catch (final FetchException e) {
					log(e.getShortMessage(), 0, 0);
					try {
						FreenetURI insertUri = null;
						
						final IDatabaseBlock databaseBlock = plugin.databaseDAO.read(topBlockUri.toString());
						if (databaseBlock != null) {
							insertUri = Client.insert(topBlockUri, databaseBlock.getData(), plugin.getFreenetClient());
						}
						
						if (insertUri != null) {
							if (topBlockUri.equals(insertUri)) {
								log("Successfully inserted top block: " + insertUri.toString(), 0);
							} else {
								log("Top block insertion failed - different uri: " + insertUri.toString(), 0);
							}
						} else {
							log("Top block insertion failed (insertUri = null)", 0);
						}
					} catch (final InsertException e1) {
						log(e1.getMessage(), 0, 0);
					}
				}
				plugin.databaseDAO.lastAccessUpdate(topBlockUri.toString());
			}
			
			// register uri
			registerManifestUri(uri, -1);
			
			// load list of keys (if exists)
			// skip if 1 because the manifest failed to fetch before.
			final int numBlocks = uriValue.getBlockCount();
			plugin.log("numBlocks Check: %s", (Object) numBlocks);
			if (numBlocks > 1) {
				log("*** loading list of blocks ***", 0, 0);
			} else {
				// parse metadata
				log("*** parsing data structure ***", 0, 0);
				parsedSegmentId = -1;
				parsedBlockId = -1;
				while (manifestURIs.size() > 0) {
					if (isInterrupted()) {
						return;
					}
					
					if (!isActive()) {
						plugin.log("Stop after stuck state (metadata)", 0);
						return;
					}
					
					uri = (FreenetURI) manifestURIs.keySet().toArray()[0];
					log(uri.toString(), 0);
					try {
						parseMetadata(uri, null, 0);
					} catch (final FetchFailedException e) {
						log(e.getMessage(), 0);
						return;
					}
					manifestURIs.remove(uri);
				}
				
				if (isInterrupted()) {
					return;
				}
				
				plugin.log("Block update: Count: %s | Blocks: %s", uriValue.getBlockCount(), uriValue.getBlocks().size());
				plugin.uriPropsDAO.update(uriValue);
			}
			
			// max segment id
			int maxSegmentId = -1;
			for (final IBlock block : uriValue.getBlocks().values()) {
				maxSegmentId = Math.max(maxSegmentId, block.getSegmentId());
			}
			
			// init reinsertion
			if (uriValue.getSegment() == maxSegmentId) {
				uriValue.setSegment(-1);
			}
			if (uriValue.getSegment() == -1) {
				
				log("*** starting reinsertion ***", 0, 0);
				
				// reset success counter
				final StringBuilder success = new StringBuilder();
				final StringBuilder segmentsSuccess = new StringBuilder();
				for (int i = 0; i <= maxSegmentId; i++) {
					if (i > 0)
						success.append(",");
					
					success.append("0,0");
					segmentsSuccess.append("0");
				}
				uriValue.setSuccess(success.toString());
				uriValue.setSuccessSegments(segmentsSuccess.toString());
				plugin.uriPropsDAO.update(uriValue);
				
			} else {
				
				log("*** continuing reinsertion ***", 0, 0);
				
				// add dummy segments
				for (int i = 0; i <= uriValue.getSegment(); i++) {
					segments.add(null);
				}
				
				// reset success counter
				final String[] successProp = uriValue.getSuccess().split(",");
				for (int i = (uriValue.getSegment() + 1) * 2; i < successProp.length; i++) {
					successProp[i] = "0";
				}
				saveSuccessToProp(successProp);
				
			}
			
			// start reinsertion
			boolean doReinsertions = true;
			timeLeft -= System.currentTimeMillis() - startedAt;
			for (long timeSpent = 0; timeLeft - timeSpent > 0; timeSpent = System.currentTimeMillis() - startedAt, timeLeft -= timeSpent) {
				startedAt = System.currentTimeMillis();
				
				if (isInterrupted()) {
					return;
				}
				
				// next segment
				int segmentSize = 0;
				for (final IBlock block : uriValue.getBlocks().values()) {
					if (block.getSegmentId() == segments.size()) {
						segmentSize++;
					}
				}
				if (segmentSize == 0) {
					break; // ready
				}
				final Segment segment = new Segment(this, segments.size(), segmentSize);
				for (final IBlock block : uriValue.getBlocks().values()) {
					if (block.getSegmentId() == segments.size()) {
						segment.addBlock(block);
					}
				}
				segments.add(segment);
				log(segment, "*** segment size: " + segment.size(), 0);
				doReinsertions = true;
				
				// get persistence rate of splitfile segments
				if (segment.size() > 1) {
					log(segment, "starting availability check for segment (n=" +
							plugin.getIntProp(PropertiesKey.SPLITFILE_TEST_SIZE) + ")", 0);
					
					// select prove blocks
					final List<IBlock> requestedBlocks = new ArrayList<>();
					// always fetch exactly the configured number of blocks (or half segment size, whichever is smaller)
					final int splitfileTestSize = Math.min(
							plugin.getIntProp(PropertiesKey.SPLITFILE_TEST_SIZE),
							(int) Math.ceil(segmentSize / 2.0));
					
					for (int i = 0; requestedBlocks.size() < splitfileTestSize; i++) {
						if (i == segmentSize) {
							i = 0;
						}
						if ((Math.random() < (splitfileTestSize / (double) segmentSize))
								&& !(requestedBlocks.contains(segment.getBlock(i)))) {
							// add a block
							requestedBlocks.add(segment.getBlock(i));
						}
					}
					
					FetchBlocksResult fetchBlocksResult = fetchBlocks(requestedBlocks, segment);
					
					double persistenceRate = fetchBlocksResult.calculatePersistenceRate();
					if (persistenceRate >= (double) plugin.getIntProp(PropertiesKey.SPLITFILE_TOLERANCE) / 100) {
						doReinsertions = false;
						segment.regFetchSuccess(persistenceRate);
						updateSegmentStatistic(segment, true);
						log(segment, "availability of segment ok: " + ((int) (persistenceRate * 100)) +
								"% (approximated)", 0, 1);
						checkFinishedSegments();
						if (uriValue.getSegment() != maxSegmentId) {
							log(segment, "-> segment not reinserted; moving on will resume on next pass.", 0, 1);
							break;
						}
					} else {
						log(segment, "<b>availability of segment not ok: " +
								((int) (persistenceRate * 100)) + "% (approximated)</b>", 0, 1);
						log(segment, "-> fetch all available blocks now", 0, 1);
					}
					
					// get all available blocks and heal the segment
					if (doReinsertions) {
						// add the rest of the blocks
						for (int i = 0; i < segment.size(); i++) {
							if (!(requestedBlocks.contains(segment.getBlock(i)))) {
								// add a block
								requestedBlocks.add(segment.getBlock(i));
							}
						}
						
						fetchBlocksResult = fetchBlocks(requestedBlocks, segment);
						
						persistenceRate = fetchBlocksResult.calculatePersistenceRate();
						if (persistenceRate >= plugin.getIntProp(PropertiesKey.SPLITFILE_TOLERANCE) / 100.0) {
							doReinsertions = false;
							segment.regFetchSuccess(persistenceRate);
							updateSegmentStatistic(segment, true);
							log(segment, "availability of segment ok: " + ((int) (persistenceRate * 100)) +
									"% (exact)", 0, 1);
							checkFinishedSegments();
							if (uriValue.getSegment() != maxSegmentId) {
								log(segment, "-> segment not reinserted; moving on will resume on next pass.", 0, 1);
								break;
							}
						} else {
							log(segment, "<b>availability of segment not ok: " +
									((int) (persistenceRate * 100)) + "% (exact)</b>", 0, 1);
						}
					}
					
					if (doReinsertions) { // persistenceRate < splitfile tolerance
						// heal segment
						
						// init
						log(segment, "starting segment healing", 0, 1);
						final byte[][] dataBlocks = new byte[segment.dataSize()][];
						final byte[][] checkBlocks = new byte[segment.checkSize()][];
						final boolean[] dataBlocksPresent = new boolean[dataBlocks.length];
						final boolean[] checkBlocksPresent = new boolean[checkBlocks.length];
						for (int i = 0; i < dataBlocks.length; i++) {
							if (segment.getDataBlock(i).isFetchSuccessful()) {
								dataBlocks[i] = segment.getDataBlock(i).getBucket().toByteArray();
								dataBlocksPresent[i] = true;
							} else {
								dataBlocks[i] = new byte[CHKBlock.DATA_LENGTH];
								dataBlocksPresent[i] = false;
							}
						}
						for (int i = 0; i < checkBlocks.length; i++) {
							if (segment.getCheckBlock(i).isFetchSuccessful()) {
								checkBlocks[i] = segment.getCheckBlock(i).getBucket().toByteArray();
								checkBlocksPresent[i] = true;
							} else {
								checkBlocks[i] = new byte[CHKBlock.DATA_LENGTH];
								checkBlocksPresent[i] = false;
							}
						}
						
						// decode
						final FECCodec codec = FECCodec.getInstance(SplitfileAlgorithm.ONION_STANDARD);
						log(segment, "start decoding", 0, 1);
						try {
							codec.decode(dataBlocks, checkBlocks, dataBlocksPresent, checkBlocksPresent, CHKBlock.DATA_LENGTH);
							log(segment, "-> decoding successful", 1, 2);
						} catch (final Exception e) {
							log(segment, "<b>segment decoding (FEC) failed, do not reinsert</b>", 1, 2);
							updateSegmentStatistic(segment, false);
							segment.setHealingNotPossible(true);
							checkFinishedSegments();
							continue;
						}
						
						// encode (= build all data blocks  and check blocks from data blocks)
						log(segment, "start encoding", 0, 1);
						try {
							codec.encode(dataBlocks, checkBlocks, checkBlocksPresent, CHKBlock.DATA_LENGTH);
							log(segment, "-> encoding successful", 1, 2);
						} catch (final Exception e) {
							log(segment, "<b>segment encoding (FEC) failed, do not reinsert</b>", 1, 2);
							updateSegmentStatistic(segment, false);
							segment.setHealingNotPossible(true);
							checkFinishedSegments();
							continue;
						}
						
						// finish
						for (int i = 0; i < dataBlocks.length; i++) {
							log(segment, "dataBlock_" + i, dataBlocks[i]);
							segment.getDataBlock(i).setBucket(new ArrayBucket(dataBlocks[i]));
						}
						for (int i = 0; i < checkBlocks.length; i++) {
							log(segment, "checkBlock_" + i, checkBlocks[i]);
							segment.getCheckBlock(i).setBucket(new ArrayBucket(checkBlocks[i]));
						}
						log(segment, "segment healing (FEC) successful, start with reinsertion", 0, 1);
						updateSegmentStatistic(segment, true);
					}
				}
				
				// start reinsertion
				if (doReinsertions) {
					log(segment, "starting reinsertion", 0, 1);
					segment.initInsert();
					
					final ExecutorService executor = Executors.newFixedThreadPool(plugin.getIntProp(PropertiesKey.POWER));
					try {
						for (int i = 0; i < segment.size(); i++) {
							checkFinishedSegments();
							isActive(true);
							if (segment.size() > 1) {
								if (segment.getBlock(i).isFetchSuccessful()) {
									segment.regFetchSuccess(true);
								} else {
									segment.regFetchSuccess(false);
									final SingleInsert singleInsert = new SingleInsert(this, segment.getBlock(i));
									executor.execute(singleInsert);
								}
							} else {
								final SingleInsert singleInsert = new SingleInsert(this, segment.getBlock(i));
								executor.execute(singleInsert);
							}
						}
						executor.shutdown();
						final boolean done = executor.awaitTermination(1, TimeUnit.HOURS);
						if (!done) {
							log(segment, "<b>reinsertion failed</b>", 0);
							return;
						}
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					} finally {
						if (!executor.isShutdown()) {
							executor.shutdownNow();
						}
					}
					
				}
				
				// check if segments are finished
				checkFinishedSegments();
			}
			
			// wait for finishing top block, if it was fetched.
			if (segments.size() > 0 && segments.get(0) != null) {
				while (!(segments.get(0).isFinished())) {
					synchronized (this) {
						try {
							this.wait(1000);
						} catch (final InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
					
					if (isInterrupted()) {
						return;
					}
					
					if (!isActive()) {
						plugin.log("Stop after stuck state (wait for finishing top block)", 0);
						return;
					}
					
					checkFinishedSegments();
				}
			}
			
			// wait for finishing all segments
			if (doReinsertions) {
				while (uriValue.getSegment() != maxSegmentId) {
					synchronized (this) {
						try {
							this.wait(1_000);
						} catch (final InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
					
					if (isInterrupted()) {
						return;
					}
					
					if (!isActive()) { // TODO: this is a bypass
						plugin.log("Stop after stuck state (after healing, prop segment_" + siteId + "=" +
								uriValue.getSegment() + ", maxSegmentId=" + maxSegmentId + ")", 0);
						// TODO: probably segment_siteId prop should be incremented (switched to next segment)
						return;
					}
					
					checkFinishedSegments();
				}
			}
			
			// add to history if we've processed the last segment in the file.
			if (uriValue.getBlockCount() > 0 && uriValue.getSegment() == maxSegmentId) {
				int nPersistence = (int) ((double) plugin.getSuccessValues(uriValue).getSuccess()
						/ uriValue.getBlockCount() * 100);
				String cHistory = uriValue.getHistory();
				String[] aHistory;
				if (cHistory == null || cHistory.trim().isEmpty()) {
					aHistory = new String[] {};
				} else {
					aHistory = cHistory.split(",");
				}
				final String cThisMonth = (new SimpleDateFormat("MM.yyyy")).format(new Date());
				boolean bNewMonth = true;
				if (cHistory != null && cHistory.contains(cThisMonth)) {
					bNewMonth = false;
					final int nOldPersistence = Integer.parseInt(aHistory[aHistory.length - 1].split("-")[1]);
					nPersistence = Math.min(nPersistence, nOldPersistence);
					aHistory[aHistory.length - 1] = cThisMonth + "-" + nPersistence;
				}
				final StringBuilder buf = new StringBuilder();
				for (final String aHistory1 : aHistory) {
					if (buf.length() > 0) {
						buf.append(",");
					}
					buf.append(aHistory1);
				}
				if (bNewMonth) {
					if (cHistory != null && cHistory.length() > 0) {
						buf.append(",");
					}
					buf.append(cThisMonth).append("-").append(nPersistence);
				}
				cHistory = buf.toString();
				
				uriValue.setHistory(cHistory);
				plugin.uriPropsDAO.update(uriValue);
			}
			
			log("*** reinsertion finished ***", 0, 0);
			plugin.log("reinsertion finished for " + uriValue.getUriString(), 1);
			
		} catch (final Exception e) {
			plugin.log("Reinserter.run()", e);
		} finally {
			latch.countDown();
			log("stopped", 0);
			plugin.log("reinserter stopped (" + siteId + ")");
		}
	}
	
	private FetchBlocksResult fetchBlocks(List<IBlock> requestedBlocks, Segment segment) {
		final ExecutorService executor = Executors.newFixedThreadPool(plugin.getIntProp(PropertiesKey.POWER));
		final FetchBlocksResult fetchBlocksResult = new FetchBlocksResult();
		try {
			Stream<Future<Boolean>> futures = requestedBlocks.stream().map(requestedBlock -> executor.submit(new SingleFetch(this, requestedBlock, true)));
			executor.shutdown();
			final boolean done = executor.awaitTermination(1, TimeUnit.HOURS);
			if (!done) {
				log(segment, "<b>availability check failed</b>", 0);
				return null;
			}
			
			futures.forEach(x -> {
				try {
					fetchBlocksResult.addResult(x.get());
				} catch (InterruptedException | ExecutionException e) {
					fetchBlocksResult.addResult(false);
					plugin.log("Reinserter.fetchBlocks(): " + e.getMessage(), e);
				}
			});
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} finally {
			if (!executor.isShutdown())
				executor.shutdownNow();
		}
		
		return fetchBlocksResult;
	}

	private void checkFinishedSegments() throws DAOException {
		int segment;
		while ((segment = uriValue.getSegment()) < segments.size() - 1) {
			if (!segments.get(segment + 1).isFinished()) {
				break;
			}
			uriValue.setSegment(segment + 1);
		}
		
		plugin.uriPropsDAO.update(uriValue);
	}
	
	private void parseMetadata(FreenetURI uri, Metadata metadata, int level)
			throws FetchFailedException, MetadataParseException, FetchException, IOException {
		if (isInterrupted()) {
			return;
		}
		
		// register uri
		registerBlockUri(uri, true, true, level);
		
		// constructs top level simple manifest (= first action on a new uri)
		if (metadata == null) {
			final FetchResult fetchResult = Client.fetch(uri, plugin.getFreenetClient());
			
			final IDatabaseBlock databaseBlock = plugin.databaseDAO.read(uri.toString());
			if (databaseBlock == null) {
				plugin.databaseDAO.create(uri.toString(), fetchResult.asByteArray());
			} else {
				databaseBlock.setData(fetchResult.asByteArray());
				plugin.databaseDAO.update(databaseBlock);
			}
			
			metadata = fetchManifest(uri, null, null);
			if (metadata == null) {
				log("no metadata", level);
				return;
			}
		}
		
		// internal manifest (simple manifest)
		if (metadata.isSimpleManifest()) {
			log("manifest (" + getMetadataType(metadata) + "): " + metadata.getResolvedName(), level);
			HashMap<String, Metadata> targetList = null;
			try {
				targetList = metadata.getDocuments();
			} catch (final Exception ignored) {}
			
			if (targetList != null) {
				for (final Entry<String, Metadata> entry : targetList.entrySet()) {
					if (isInterrupted()) {
						return;
					}
					// get document
					final Metadata target = entry.getValue();
					// remember document name
					target.resolve(entry.getKey());
					// parse document
					parseMetadata(uri, target, level + 1);
				}
			}
			
			return;
		}
		
		// redirect to submanifest
		if (metadata.isArchiveMetadataRedirect()) {
			log("document (" + getMetadataType(metadata) + "): " + metadata.getResolvedName(), level);
			final Metadata subManifest = fetchManifest(uri, metadata.getArchiveType(), metadata.getArchiveInternalName());
			parseMetadata(uri, subManifest, level);
			return;
		}
		
		// internal redirect
		if (metadata.isArchiveInternalRedirect()) {
			log("document (" + getMetadataType(metadata) + "): " + metadata.getArchiveInternalName(), level);
			return;
		}
		
		// single file redirect with external key (only possible if archive manifest or simple redirect but not splitfile)
		if (metadata.isSingleFileRedirect()) {
			log("document (" + getMetadataType(metadata) + "): " + metadata.getResolvedName(), level);
			final FreenetURI targetUri = metadata.getSingleTarget();
			log("-> redirect to: " + targetUri, level);
			registerManifestUri(targetUri, level);
			registerBlockUri(targetUri, true, true, level);
			return;
		}
		
		// splitfile
		if (metadata.isSplitfile()) {
			// splitfile type
			if (metadata.isSimpleSplitfile()) {
				log("simple splitfile: " + metadata.getResolvedName(), level);
			} else {
				log("splitfile (not simple): " + metadata.getResolvedName(), level);
			}
			
			// register blocks
			final Metadata metadata2 = (Metadata) metadata.clone();
			final SplitFileSegmentKeys[] segmentKeys = metadata2.grabSegmentKeys();
			for (int i = 0; i < segmentKeys.length; i++) {
				final int dataBlocks = segmentKeys[i].getDataBlocks();
				final int checkBlocks = segmentKeys[i].getCheckBlocks();
				log("segment_" + i + ": " + (dataBlocks + checkBlocks) +
						" (data=" + dataBlocks + ", check=" + checkBlocks + ")", level + 1);
				for (int j = 0; j < dataBlocks + checkBlocks; j++) {
					final FreenetURI splitUri = segmentKeys[i].getKey(j, null, false).getURI();
					log("block: " + splitUri, level + 1);
					registerBlockUri(splitUri, (j == 0), (j < dataBlocks), level + 1);
				}
			}
			
			// create metadata from splitfile (if not simple splitfile)
			if (!metadata.isSimpleSplitfile()) {
				// TODO: move fetch to net package
				final FetchContext fetchContext = pr.getHLSimpleClient().getFetchContext();
				final ClientContext clientContext = pr.getNode().clientCore.clientContext;
				final FetchWaiter fetchWaiter = new FetchWaiter(plugin.getFreenetClient());
				final List<COMPRESSOR_TYPE> decompressors = new LinkedList<>();
				if (metadata.isCompressed()) {
					log("is compressed: " + metadata.getCompressionCodec(), level + 1);
					decompressors.add(metadata.getCompressionCodec());
				} else {
					log("is not compressed", level + 1);
				}
				final SplitfileGetCompletionCallback cb = new SplitfileGetCompletionCallback(fetchWaiter, plugin);
				final VerySimpleGetter vsg = new VerySimpleGetter((short) 2, null, plugin.getFreenetClient());
				final SplitFileFetcher sf = new SplitFileFetcher(metadata, cb, vsg,
						fetchContext, true, decompressors,
						metadata.getClientMetadata(), 0L, metadata.topDontCompress,
						metadata.topCompatibilityMode.code, false, metadata.getResolvedURI(),
						true, clientContext);
				sf.schedule(clientContext);
				
				// fetchWaiter.waitForCompletion();
				while (cb.getDecompressedData() == null) { // workaround because in some cases fetchWaiter.waitForCompletion() never finished
					if (isInterrupted()) {
						return;
					}
					
					if (!isActive()) {
						throw new FetchFailedException("Manifest cannot be fetched");
					}
					
					synchronized (this) {
						try {
							wait(100);
						} catch (final InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
				}
				sf.cancel(clientContext);
				metadata = fetchManifest(cb.getDecompressedData(), null, null);
				parseMetadata(null, metadata, level + 1);
			}
		}
	}
	
	private String getMetadataType(Metadata metadata) {
		try {
			
			String types = "";
			
			if (metadata.isArchiveManifest())
				types += ",AM";
			
			if (metadata.isSimpleManifest())
				types += ",SM";
			
			if (metadata.isArchiveInternalRedirect())
				types += ",AIR";
			
			if (metadata.isArchiveMetadataRedirect())
				types += ",AMR";
			
			if (metadata.isSymbolicShortlink())
				types += ",SSL";
			
			if (metadata.isSingleFileRedirect())
				types += ",SFR";
			
			if (metadata.isSimpleRedirect())
				types += ",SR";
			
			if (metadata.isMultiLevelMetadata())
				types += ",MLM";
			
			if (metadata.isSplitfile())
				types += ",SF";
			
			if (metadata.isSimpleSplitfile())
				types += ",SSF";
			
			// remove first comma
			if (types.length() > 0)
				types = types.substring(1);
			
			return types;
		} catch (final Exception e) {
			plugin.log("Reinserter.getMetadataType(): " + e.getMessage(), e);
			return null;
		}
	}
	
	public Plugin getPlugin() {
		return plugin;
	}
	
	private Metadata fetchManifest(FreenetURI uri, ARCHIVE_TYPE archiveType, String manifestName)
			throws FetchException, IOException {
		final FetchResult result = Client.fetch(uri, plugin.getFreenetClient());
		
		return fetchManifest(result.asByteArray(), archiveType, manifestName);
	}
	
	private Metadata fetchManifest(byte[] data, ARCHIVE_TYPE archiveType, String manifestName) throws IOException {
		Metadata metadata = null;
		try (ByteArrayInputStream fetchedDataStream = new ByteArrayInputStream(data)) {
			
			if (manifestName == null) {
				manifestName = ".metadata";
			}
			
			if (archiveType == null) {
				// try to construct metadata directly
				try {
					metadata = Metadata.construct(data);
				} catch (final MetadataParseException ex) {/* ignored */}
			}
			
			if (metadata == null) {
				// unzip and construct metadata
				try {
					InputStream inStream = null;
					String entryName = null;
					
					// get archive stream (try if archive type unknown)
					if (archiveType == ARCHIVE_TYPE.TAR || archiveType == null) {
						inStream = new TarInputStream(fetchedDataStream);
						entryName = ((TarInputStream) inStream).getNextEntry().getName();
						archiveType = ARCHIVE_TYPE.TAR;
					}
					if (archiveType == ARCHIVE_TYPE.ZIP) {
						inStream = new ZipInputStream(fetchedDataStream);
						entryName = ((ZipInputStream) inStream).getNextEntry().getName();
						archiveType = ARCHIVE_TYPE.ZIP;
					}
					
					// construct metadata
					while (inStream != null && entryName != null) {
						if (entryName.equals(manifestName)) {
							final byte[] buf = new byte[32768];
							final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
							int bytes;
							while ((bytes = inStream.read(buf)) > 0) {
								outStream.write(buf, 0, bytes);
							}
							outStream.close();
							metadata = Metadata.construct(outStream.toByteArray());
							break;
						}
						if (archiveType == ARCHIVE_TYPE.TAR) {
							entryName = ((TarInputStream) inStream).getNextEntry().getName();
						} else {
							entryName = ((ZipInputStream) inStream).getNextEntry().getName();
						}
					}
					
				} catch (final Exception e) {
					if (archiveType != null)
						log("unzip and construct metadata: " + e.getMessage(), 0, 2);
				}
			}
			
			if (metadata != null) {
				if (archiveType != null) {
					manifestName += " (" + archiveType.name() + ")";
				}
				metadata.resolve(manifestName);
			}
			return metadata;
			
		}
	}
	
	private FreenetURI updateUsk(FreenetURI uri) {
		try {
			Client.fetch(uri, plugin.getFreenetClient());
		} catch (final freenet.client.FetchException e) {
			if (e.getMode() == FetchException.FetchExceptionMode.PERMANENT_REDIRECT) {
				uri = updateUsk(e.newURI);
			}
		}
		
		return uri;
	}
	
	private void registerManifestUri(FreenetURI uri, int level) {
		uri = Client.normalizeUri(uri);
		if (manifestURIs.containsKey(uri)) {
			log("-> already registered manifest", level, 2);
		} else {
			manifestURIs.put(uri, null);
			if (level != -1) {
				log("-> registered manifest", level, 2);
			}
		}
	}
	
	private void registerBlockUri(FreenetURI uri, boolean newSegment, boolean isDataBlock, int logTabLevel) {
		if (uri != null) { // uri is null if metadata is created from splitfile
			
			// no reinsertion for SSK but go to sublevel
			if (!uri.isCHK()) {
				log("-> no reinsertion of USK, SSK or KSK", logTabLevel, 2);
				
				// check if uri already reinserted during this session
			} else if (uriValue.getBlocks().containsKey(Client.normalizeUri(uri))) {
				log("-> already registered block", logTabLevel, 2);
				
				// register
			} else {
				if (newSegment) {
					parsedSegmentId++;
					parsedBlockId = -1;
				}
				uri = Client.normalizeUri(uri);
				uriValue.getBlocks().put(uri, new Block(uri, parsedSegmentId, ++parsedBlockId, isDataBlock));
				log("-> registered block", logTabLevel, 2);
			}
			
		}
	}
	
	public void registerBlockFetchSuccess(IBlock block) {
		try {
			segments.get(block.getSegmentId()).regFetchSuccess(block.isFetchSuccessful());
		} catch (final DAOException e) {
			plugin.log("Problem with a DAO at Block: %s", e, block);
		}
	}
	
	public synchronized void updateSegmentStatistic(Segment segment, boolean success) throws DAOException {
		String successProp = uriValue.getSuccessSegments();
		if (success) {
			successProp = successProp.substring(0, segment.getId()) + "1" + successProp.substring(segment.getId() + 1);
		}
		uriValue.setSuccessSegments(successProp);
		plugin.uriPropsDAO.update(uriValue);
	}
	
	public synchronized void updateBlockStatistic(int id, int success, int failed) throws DAOException {
		final String[] successProp = uriValue.getSuccess().split(",");
		successProp[id * 2] = String.valueOf(success);
		successProp[id * 2 + 1] = String.valueOf(failed);
		saveSuccessToProp(successProp);
	}
	
	private void saveSuccessToProp(String[] success) throws DAOException {
		final String newSuccess = String.join(",", success);
		uriValue.setSuccess(newSuccess);
		plugin.uriPropsDAO.update(uriValue);
	}
	
	public boolean isActive() {
		return isActive(false);
	}
	
	private boolean isActive(boolean newActivity) {
		if (newActivity) {
			lastActivityTime = System.currentTimeMillis();
			return true;
		}
		if (lastActivityTime != Integer.MIN_VALUE) {
			final long delay = (System.currentTimeMillis() - lastActivityTime) / 60_000; // delay in minutes
			return (delay < SingleJob.MAX_LIFETIME + 5);
		}
		return false;
	}
	
	public void log(int segmentId, String message, int level, int logLevel) {
		final StringBuilder buf = new StringBuilder();
		
		for (int i = 0; i < level; i++) {
			buf.append("    ");
		}
		
		if (segmentId != -1) {
			buf.insert(0, "(" + segmentId + ") ");
		}
		
		try {
			if (plugin.getIntProp(PropertiesKey.LOG_LINKS) == 1) {
				int keyPos = message.indexOf("K@");
				if (keyPos != -1) {
					keyPos = keyPos - 2;
					int keyPos2 = Math.max(message.indexOf(" ", keyPos), message.indexOf("<", keyPos));
					if (keyPos2 == -1) {
						keyPos2 = message.length();
					}
					final String key = message.substring(keyPos, keyPos2);
					message = message.substring(0, keyPos) +
							"<a href=\"/" + key + "\">" + key + "</a>" + message.substring(keyPos2);
				}
			}
		} catch (final Exception ex) {/* ignore */}
		
		plugin.logFile(plugin.getLogFilename(uriValue), buf.append(message).toString(), logLevel);
	}
	
	public void log(Segment segment, String message, int level, int logLevel) {
		log(segment.getId(), message, level, logLevel);
	}
	
	public void log(Segment segment, String message, int level) {
		log(segment, message, level, 1);
	}
	
	public void log(String message, int level, int logLevel) {
		log(-1, message, level, logLevel);
	}
	
	public void log(String message, int level) {
		log(-1, message, level, 1);
	}
	
	public void log(Segment segment, String cMessage, Object obj) {
		if (obj != null) {
			log(segment, cMessage + " = ok", 1, 2);
		} else {
			log(segment, cMessage + " = null", 1, 2);
		}
	}
	
	public List<Segment> getSegments() {
		return segments;
	}
	
}
