/*
 * Plugin Base Package
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
package pluginbase;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;

import pluginbase.de.todesbaum.util.freenet.fcp2.Command;
import pluginbase.de.todesbaum.util.freenet.fcp2.Connection;

abstract public class FcpCommandBase extends Command {
	
	private PageBase page;
	private String strCommandName;
	private InputStream dataStream;
	private int nDataLength;
	private final Connection fcpConnection;
	private final ArrayList<String> vFields = new ArrayList<>();
	
	FcpCommandBase(Connection fcpConnection, PageBase page) {
		super(null, null);
		this.fcpConnection = fcpConnection;
		this.page = page;
	}
	
	public FcpCommandBase(Connection fcpConnection) {
		super(null, null);
		this.fcpConnection = fcpConnection;
	}
	
	@Override
	public String getCommandName() {
		return strCommandName;
	}
	
	void setCommandName(String strCommandName) {
		this.strCommandName = strCommandName;
	}
	
	private String getIdentifier(String cIdentifier) throws Exception {
		try {
			
			return page.getName() + "_" + cIdentifier + "_" + System.currentTimeMillis();
			
		} catch (final Exception e) {
			throw new Exception("FcpCommandBase.getIdentifier(): " + e.getMessage());
		}
	}
	
	@Override
	protected boolean hasPayload() {
		return (dataStream != null);
	}
	
	@Override
	protected InputStream getPayload() {
		return dataStream;
	}
	
	@Override
	protected long getPayloadLength() {
		return nDataLength;
	}
	
	@Override
	protected void write(Writer writer) throws IOException {
		try {
			
			for (final String cField : vFields) {
				writer.write(cField + "\n");
			}
			
		} catch (final IOException e) {
			page.log("FcpCommandBase.write(): " + e.getMessage(), 1);
			throw new IOException("FcpCommandBase.write(): " + e.getMessage());
		}
	}
	
	protected void init(String strCommand, String strIdentifierSuffix) throws Exception {
		try {
			
			if (strIdentifierSuffix == null) {
				strIdentifierSuffix = "";
			}
			vFields.clear();
			setCommandName(strCommand);
			field("Identifier", getIdentifier(strIdentifierSuffix));
			
		} catch (final Exception e) {
			throw new Exception("FcpCommandBase.init(): " + e.getMessage());
		}
	}
	
	protected void field(String strKey, String strValue) throws Exception {
		try {
			
			vFields.add(strKey + "=" + strValue);
			
		} catch (final Exception e) {
			throw new Exception("FcpCommandBase.field(): " + e.getMessage());
		}
	}
	
	protected void field(String strKey, int nValue) throws Exception {
		try {
			
			vFields.add(strKey + "=" + nValue);
			
		} catch (final Exception e) {
			throw new Exception("FcpCommandBase.field(): " + e.getMessage());
		}
	}
	
	protected void send(InputStream dataStream, int nDataLength) throws Exception {
		try {
			
			this.dataStream = dataStream;
			this.nDataLength = nDataLength;
			fcpConnection.execute(this);
			
		} catch (IllegalStateException | IOException e) {
			throw new Exception("FcpCommandBase.send(): " + e.getMessage());
		}
	}
	
	protected void send() throws Exception {
		send(null, 0);
	}
	
}
