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

import java.util.Arrays;
import java.util.Objects;

import keepalive.exceptions.DAOException;
import keepalive.service.reinserter.Reinserter;

public class Segment {
	
	private final int id;
	private final int size;
	private IBlock[] blocks;
	private int dataBlocksCount;
	private int success = 0;
	private int failed = 0;
	private boolean persistenceCheckOk = false;
	private boolean healingNotPossible = false;
	
	private final Reinserter reinserter;
	
	public Segment(Reinserter reinserter, int id, int size) {
		this.reinserter = reinserter;
		this.id = id;
		this.size = size;
		this.blocks = new IBlock[size];
	}
	
	public IBlock getBlock(int id) {
		return blocks[id];
	}
	
	public boolean addBlock(IBlock block) {
		if (block.getId() > size)
			return false;
		
		blocks[block.getId()] = block;
		if (block.isDataBlock())
			dataBlocksCount++;
		
		return true;
	}
	
	public IBlock getDataBlock(int id) {
		return blocks[id];
	}
	
	public IBlock getCheckBlock(int id) {
		return blocks[dataSize() + id];
	}
	
	public int size() {
		return size; // blocks.length can produce null-exception (see isFinished())
	}
	
	public int dataSize() {
		return dataBlocksCount;
	}
	
	public int checkSize() {
		return size - dataBlocksCount;
	}
	
	public void initInsert() {
		success = 0;
		failed = 0;
	}
	
	public void regFetchSuccess(double persistenceRate) throws DAOException {
		persistenceCheckOk = true;
		success = (int) Math.round(persistenceRate * size);
		failed = size - success;
		reinserter.updateBlockStatistic(id, success, failed);
	}
	
	public void regFetchSuccess(boolean isSuccess) throws DAOException {
		if (isSuccess)
			success++;
		else
			failed++;
		
		reinserter.updateBlockStatistic(id, success, failed);
	}
	
	public int getId() {
		return id;
	}
	
	public boolean isFinished() {
		if (blocks == null)
			return true;
		
		boolean finished = true;
		if (!persistenceCheckOk && !healingNotPossible) {
			if (size == 1) {
				finished = getBlock(0).isInsertDone();
			} else {
				for (final IBlock block : blocks) {
					if (block != null && !block.isFetchSuccessful() && !block.isInsertDone()) {
						finished = false;
						break;
					}
				}
			}
		}
		
		// free blocks (especially buckets)
		if (finished) {
			for (final IBlock block : blocks) {
				if (block != null && block.getBucket() != null)
					block.getBucket().free();
			}
			blocks = null;
		}
		
		return finished;
	}
	
	public void setHealingNotPossible(boolean notPossible) {
		healingNotPossible = notPossible;
	}
	
	@Override
	public String toString() {
		return String.format("Segment [id=%s, size=%s, blocks=%s, dataBlocksCount=%s, success=%s, failed=%s, persistenceCheckOk=%s, healingNotPossible=%s, reinserter=%s]", id, size,
				Arrays.toString(blocks), dataBlocksCount, success, failed, persistenceCheckOk, healingNotPossible, reinserter);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(blocks);
		return prime * result + Objects.hash(dataBlocksCount, failed, healingNotPossible, id, persistenceCheckOk, reinserter, size, success);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		final Segment other = (Segment) obj;
		return Arrays.equals(blocks, other.blocks) && dataBlocksCount == other.dataBlocksCount && failed == other.failed && healingNotPossible == other.healingNotPossible && id == other.id
				&& persistenceCheckOk == other.persistenceCheckOk && Objects.equals(reinserter, other.reinserter) && size == other.size && success == other.success;
	}
	
}
