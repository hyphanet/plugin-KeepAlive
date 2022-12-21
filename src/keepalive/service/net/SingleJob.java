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
package keepalive.service.net;

import freenet.keys.FreenetURI;
import freenet.support.compress.Compressor;
import keepalive.Plugin;
import keepalive.model.IBlock;
import keepalive.service.reinserter.Reinserter;

public abstract class SingleJob {
	
	public static final int MAX_LIFETIME = 30;
	
	protected Plugin plugin;
	protected Reinserter reinserter;
	protected IBlock block;
	protected byte[] uriExtra;
	protected String compressionAlgorithm;
	
	private final String jobType;
	
	protected SingleJob(Reinserter reinserter, String jobType, IBlock block) {
		this.reinserter = reinserter;
		this.jobType = jobType;
		this.block = block;
		this.plugin = reinserter.getPlugin();
	}
	
	protected FreenetURI getUri() {
		final FreenetURI uri = block.getUri().clone();
		
		// modify the control flag of the URI to get always the raw data
		uriExtra = uri.getExtra();
		uriExtra[2] = 0;
		
		// get the compression algorithm of the block
		if (uriExtra[4] >= 0) {
			compressionAlgorithm = Compressor.COMPRESSOR_TYPE.getCompressorByMetadataID(uriExtra[4]).name;
		} else {
			compressionAlgorithm = "none";
		}
		
		log(String.format("request: %s (crypt=%s,control=%s,compress=%s=%s)", block.getUri(), uriExtra[1], block.getUri().getExtra()[2], uriExtra[4], compressionAlgorithm), 2);
		
		return uri;
	}
	
	protected void finish() {
		if (reinserter.isActive() && !reinserter.isInterrupted()) {
			String msg = String.format("%s: %s -> %s", jobType, block.getResultLog(), block.getUri());
			
			// error or problem
			if (!block.isFetchSuccessful() && !block.isInsertSuccessful())
				msg = String.format("<b>%s</b>", msg);
			
			log(msg);
		}
	}
	
	protected void log(String message, int logLevel) {
		if (reinserter.isActive() && !Thread.currentThread().isInterrupted() && !reinserter.isInterrupted()) {
			reinserter.log(block.getSegmentId(), message, 0, logLevel);
		}
	}
	
	protected void log(String message) {
		log(message, 1);
	}
	
}
