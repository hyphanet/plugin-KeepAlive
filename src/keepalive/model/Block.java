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
package keepalive.model;

import java.util.Objects;

import freenet.keys.FreenetURI;
import freenet.support.io.ArrayBucket;

public class Block implements IBlock {
	
	private final int id;
	private final int segmentId;
	private final FreenetURI uri;
	private ArrayBucket bucket;
	private final boolean dataBlock;
	private boolean fetchDone; // done but not necessarily successful
	private boolean fetchSuccessful;
	private boolean insertDone; // done but not necessarily successful
	private boolean insertSuccessful;
	private String resultLog;
	
	public Block(FreenetURI uri, int segmentId, int id, boolean isDataBlock) {
		this.uri = uri;
		this.segmentId = segmentId;
		this.id = id;
		this.dataBlock = isDataBlock;
	}
	
	@Override
	public int getId() {
		return id;
	}
	
	@Override
	public int getSegmentId() {
		return segmentId;
	}
	
	@Override
	public FreenetURI getUri() {
		return uri;
	}
	
	@Override
	public ArrayBucket getBucket() {
		return bucket;
	}
	
	@Override
	public void setBucket(ArrayBucket bucket) {
		this.bucket = bucket;
	}
	
	@Override
	public boolean isDataBlock() {
		return dataBlock;
	}
	
	@Override
	public boolean isFetchInProcess() {
		return !fetchDone;
	}
	
	@Override
	public void setFetchDone(boolean done) {
		fetchDone = done;
	}
	
	@Override
	public boolean isInsertDone() {
		return insertDone;
	}
	
	@Override
	public void setInsertDone(boolean done) {
		insertDone = done;
	}
	
	@Override
	public boolean isInsertSuccessful() {
		return insertSuccessful;
	}
	
	@Override
	public void setInsertSuccessful(boolean successful) {
		insertSuccessful = successful;
	}
	
	@Override
	public boolean isFetchSuccessful() {
		return fetchSuccessful;
	}
	
	@Override
	public void setFetchSuccessful(boolean successful) {
		fetchSuccessful = successful;
	}
	
	@Override
	public String getResultLog() {
		return resultLog;
	}
	
	@Override
	public void setResultLog(String result) {
		resultLog = result;
	}
	
	@Override
	public void appendResultLog(String result) {
		resultLog += result;
	}
	
	@Override
	public String toString() {
		return String.format("Block [id=%s, segmentId=%s, uri=%s, bucket=%s, dataBlock=%s, fetchDone=%s, fetchSuccessful=%s, insertDone=%s, insertSuccessful=%s, resultLog=%s]", id, segmentId, uri,
				bucket, dataBlock, fetchDone, fetchSuccessful, insertDone, insertSuccessful, resultLog);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(bucket, dataBlock, fetchDone, fetchSuccessful, id, insertDone, insertSuccessful, resultLog, segmentId, uri);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		final Block other = (Block) obj;
		return Objects.equals(bucket, other.bucket) && dataBlock == other.dataBlock && fetchDone == other.fetchDone && fetchSuccessful == other.fetchSuccessful && id == other.id
				&& insertDone == other.insertDone && insertSuccessful == other.insertSuccessful && Objects.equals(resultLog, other.resultLog) && segmentId == other.segmentId
				&& Objects.equals(uri, other.uri);
	}
	
}
