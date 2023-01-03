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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;

import freenet.clients.http.PageMaker;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;
import keepalive.model.PropertiesKey;
import pluginbase.de.todesbaum.util.freenet.fcp2.Connection;
import pluginbase.de.todesbaum.util.freenet.fcp2.ConnectionListener;
import pluginbase.de.todesbaum.util.freenet.fcp2.Message;
import pluginbase.de.todesbaum.util.freenet.fcp2.Node;

public abstract class PluginBase implements FredPlugin, FredPluginThreadless,
		FredPluginVersioned, FredPluginL10n, ConnectionListener {
	
	public PluginContext pluginContext;
	
	PageMaker pagemaker;
	WebInterface webInterface;
	Connection fcpConnection;
	LANGUAGE nodeLanguage;
	
	private Properties prop;
	private String strTitle;
	private String strPath;
	private String strPropFilename;
	private String strMenuTitle = null;
	private String strMenuTooltip = null;
	private String strVersion = "0.0";
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd_HH.mm_ss");
	private final Map<String, PageBase> mPages = new TreeMap<>();
	private final Map<String, RandomAccessFile> mLogFiles = new TreeMap<>();
	private final boolean stackTrace = true;// FIXME "true".equals(getProp(PropertiesKey.STACKTRACE));
	
	protected PluginBase(String strPath, String strTitle, String strPropFilename) {
		try {
			this.strPath = strPath;
			this.strTitle = strTitle;
			this.strPropFilename = strPropFilename;
			
			// prepare and clear log file
			(new File(strPath)).mkdir();
			initLog("log.txt");
			
			dateFormat.setTimeZone(TimeZone.getDefault());
			
			// load properties
			loadProp();
			if (getProp(PropertiesKey.LOGLEVEL) == null) {
				setIntProp(PropertiesKey.LOGLEVEL, 0);
			}
			
		} catch (final Exception e) {
			log("PluginBase(): " + e.getMessage(), e);
		}
	}
	
	String getCategory() {
		return strTitle.replace(' ', '_');
	}
	
	String getPath() {
		return "/" + strPath;
	}
	
	public String getPluginDirectory() {
		return strPath + "/";
	}
	
	@Override
	public void runPlugin(PluginRespirator pr) { // FredPlugin
		try {
			log(getVersion() + " started");
			
			// init plugin context
			pagemaker = pr.getPageMaker();
			pluginContext = new PluginContext(pr);
			webInterface = new WebInterface(pluginContext);
			
			// fcp connection
			connectFcp();
			
			// add menu
			pagemaker.removeNavigationCategory(getCategory());
			if (strMenuTitle != null) {
				webInterface.addNavigationCategory(getPath() + "/", getCategory(), strMenuTooltip, this);
			}
		} catch (final Exception e) {
			log("PluginBase.runPlugin(): " + e.getMessage(), 1);
		}
	}
	
	@Override
	public String getVersion() { // FredPluginVersioned
		return strTitle + " " + strVersion;
	}
	
	/**
	 *
	 * @param strKey
	 * @return This method return phrase in right local
	 */
	@Override
	public String getString(String strKey) { // FredPluginL10n
		return strKey;
	}
	
	/**
	 *
	 * @param language
	 */
	@Override
	public void setLanguage(LANGUAGE language) { // FredPluginL10n
		nodeLanguage = language;
	}
	
	@Override
	public void terminate() { // FredPlugin
		try {
			
			log("plugin base terminates");
			saveProp();
			fcpConnection.disconnect();
			fcpConnection = null;
			webInterface.kill();
			webInterface = null;
			pagemaker.removeNavigationCategory(getCategory());
			log("plugin base terminated");
			for (final RandomAccessFile file : mLogFiles.values()) {
				file.close();
			}
			
		} catch (final IOException e) {
			log("PluginBase.terminate(): " + e.getMessage(), 1);
		}
	}
	
	@Override
	public void messageReceived(Connection connection, Message message) { // ConnectionListener
		try {
			// errors
			if ("ProtocolError".equals(message.getName())) {
				log("ProtocolError: " + message.get("CodeDescription"));
			} else if ("IdentifierCollision".equals(message.getName())) {
				log("IdentifierCollision");
			} else if ("GetFailed".equals(message.getName()) &&
					(message.get("RedirectUri") != null) && !"".equals(message.get("RedirectUri"))) {
				// redirect deprecated usk edition
				log("USK redirected (" + message.getIdentifier() + ")");
				
				// reg new edition
				final String pageName = message.getIdentifier().split("_")[0];
				final PageBase page = mPages.get(pageName);
				if (page != null)
					page.updateRedirectUri(message);
				
				// redirect
				final FcpCommands fcpCommand = new FcpCommands(fcpConnection, null);
				fcpCommand.setCommandName("ClientGet");
				fcpCommand.field("Identifier", message.getIdentifier());
				fcpCommand.field("URI", message.get("RedirectUri"));
				fcpCommand.send();
			} else {
				// register message
				log("fcp: " + message.getName() + " (" + message.getIdentifier() + ")");
				final String pageName = message.getIdentifier().split("_")[0];
				final PageBase page = mPages.get(pageName);
				if (page != null)
					page.addMessage(message);
			}
			
		} catch (final Exception e) {
			log("PluginBase.messageReceived(): " + e.getMessage(), 1);
		}
	}
	
	@Override
	public void connectionTerminated(Connection connection) { // ConnectionListener
		log("fcp connection terminated");
	}
	
	private synchronized void connectFcp() {
		try {
			
			if (fcpConnection == null || !fcpConnection.isConnected()) {
				fcpConnection = new Connection(new Node("localhost"), "connection_" + System.currentTimeMillis());
				fcpConnection.addConnectionListener(this);
				fcpConnection.connect();
			}
			
		} catch (final IOException e) {
			log("PluginBase.connectFcp(): " + e.getMessage(), 1);
		}
	}
	
	private void loadProp() {
		try {
			if (strPropFilename != null) {
				prop = new Properties();
				final File file = new File(strPath, strPropFilename);
				final File oldFile = new File(strPath, strPropFilename + ".old");
				
				FileInputStream is = null;
				try {
					//always load from the backup if it exists, it is (almost?)
					//guaranteed to be good.
					if (oldFile.exists()) {
						is = new FileInputStream(oldFile);
					} else if (file.exists()) {
						is = new FileInputStream(file);
					} else {
						throw new Exception("No Prop file found.");
					}
					prop.load(is);
				} finally {
					if (is != null)
						is.close();
				}
			}
		} catch (final Exception e) {
			log("PluginBase.loadProp(): " + e.getMessage(), 1);
		}
	}
	
	// ******************************************
	// methods to use in the derived page class:
	// ******************************************
	// log files
	private synchronized void initLog(String strFilename) {
		try {
			
			if (!mLogFiles.containsKey(strFilename)) {
				final RandomAccessFile file = new RandomAccessFile(strPath + "/" + strFilename, "rw");
				file.seek(file.length());
				mLogFiles.put(strFilename, file);
			}
			
		} catch (final IOException e) {
			log("PluginBase.initLog() - file: %s", e, strFilename);
		}
	}
	
	public synchronized void logFile(String strFilename, String cText, int nLogLevel) {
		try {
			
			if (nLogLevel <= getIntProp(PropertiesKey.LOGLEVEL)) {
				initLog(strFilename);
				mLogFiles.get(strFilename).writeBytes(dateFormat.format(new Date()) + "  " + cText + "\n");
			}
			
		} catch (final Exception e) {
			if (!"log.txt".equals(strFilename)) // to avoid infinite loop when log.txt was closed on shutdown
			{
				log("PluginBase.log():", e);
			}
		}
	}
	
	public void logFile(String strFilename, String strText) {
		logFile(strFilename, strText, 0);
	}
	
	public synchronized String getLog(String filename) {
		try {
			
			initLog(filename);
			final RandomAccessFile file = mLogFiles.get(filename);
			final int MAX_LOG_LENGTH = 2_000_000; // around 10k lines
			final StringBuilder buffer = new StringBuilder();
			final long fileLength = file.length();
			if (fileLength > MAX_LOG_LENGTH) {
				final long skip = fileLength - MAX_LOG_LENGTH;
				file.seek(skip);
				file.readLine();
				buffer.append("log contains ").append(skip).append(" preceding bytes (~").append(skip / 200).append(" lines)").append("\n");
			} else {
				file.seek(0);
			}
			String line;
			while ((line = file.readLine()) != null) {
				buffer.append(line).append("\n");
			}
			return buffer.toString();
			
		} catch (final IOException e) {
			log("PluginBase.getLog(): " + e.getMessage());
			return null;
		}
	}
	
	public void clearLog(String strFilename) {
		try {
			
			initLog(strFilename);
			mLogFiles.get(strFilename).setLength(0);
			
		} catch (final IOException e) {
			log("PluginBase.clearLog(): " + e.getMessage());
		}
	}
	
	public void clearAllLogs() {
		try {
			
			for (final RandomAccessFile file : mLogFiles.values()) {
				file.setLength(0);
			}
			
		} catch (final IOException e) {
			log("PluginBase.clearAllLogs(): " + e.getMessage());
		}
	}
	
	public void setLogLevel(int nLevel) throws Exception {
		try {
			
			setIntProp(PropertiesKey.LOGLEVEL, nLevel);
			
		} catch (final Exception e) {
			throw new Exception("PluginBase.setLogLevel(): " + e.getMessage(), e);
		}
	}
	
	// standard log
	public void log(String cText) {
		logFile("log.txt", cText, 0);
	}
	
	public void logF(String cText, Object... args) {
		logFile("log.txt", String.format(cText, args), 0);
	}
	
	public void log(String cText, int nLogLevel) {
		logFile("log.txt", cText, nLogLevel);
	}
	
	public void log(String info, Throwable e) {
		final StringBuilder message = new StringBuilder(String.format("%s: %s %s", info, e.getClass().getName(), e.getMessage()));
		if (stackTrace)
			message.append(System.lineSeparator()).append(stackTraceToString(e));
		
		log(message.toString());
	}
	
	public void log(String info, Throwable e, Object... args) {
		log(String.format(info, args), e);
	}
	
	private String stackTraceToString(Throwable e) {
		try (final StringWriter sw = new StringWriter();
				final PrintWriter pw = new PrintWriter(sw)) {
			e.printStackTrace(pw);
			return sw.toString();
		} catch (IOException e1) {
			return String.format("Error in stackTraceToString: %s", e1.getMessage());
		}
	}
	
	protected void clearLog() {
		clearLog("log.txt");
	}
	
	public String getLog() {
		return getLog("log.txt");
	}
	
	// methods to set the version of the plugin
	protected void setVersion(String strVersion) {
		this.strVersion = strVersion;
	}
	
	// methods to add the plugin to the nodes' main menu (fproxy)
	protected void addPluginToMenu(String strMenuTitle, String strMenuTooltip) {
		this.strMenuTitle = strMenuTitle;
		this.strMenuTooltip = strMenuTooltip;
	}
	
	// methods to add menu items to the plugins menu (fproxy)
	protected void addMenuItem(String strTitle, String strTooltip, String strUri, boolean isFullAccessHostsOnly) {
		try {
			
			pagemaker.addNavigationLink(getCategory(), strUri, strTitle, strTooltip, isFullAccessHostsOnly, null, this);
			log("item '" + strTitle + "' added to menu");
			
		} catch (final Exception e) {
			log("PluginBase.addMenuItem(): " + e.getMessage());
		}
	}
	
	// methods to build the plugin
	protected void addPage(PageBase page) {
		try {
			
			mPages.put(page.getName(), page);
			
		} catch (final Exception e) {
			log("PluginBase.addPage(): " + e.getMessage());
		}
	}
	
	// methods to set and get persistent properties
	public synchronized void saveProp() {
		if (prop == null)
			return;
		
		try {
			final File file = new File(strPath, strPropFilename);
			final File oldFile = new File(strPath, strPropFilename + ".old");
			final File newFile = new File(strPath, strPropFilename + ".new");
			
			if (newFile.exists()) {
				Files.delete(newFile.toPath());
			}
			
			try (FileOutputStream stream = new FileOutputStream(newFile)) {
				prop.store(stream, strTitle);
				stream.flush();
			}
			
			if (oldFile.exists()) {
				Files.delete(oldFile.toPath());
			}
			
			if (file.exists()) {
				file.renameTo(oldFile);
			}
			
			newFile.renameTo(file);
		} catch (final IOException e) {
			log("PluginBase.saveProp(): " + e.getMessage());
		}
	}
	
	/**
	 * {@link Properties#setProperty(String, String)}
	 * @param key
	 * @param value
	 */
	public void setProp(PropertiesKey key, String value) {
		if (key == null || value == null)
			return;
		
		prop.setProperty(key.toString(), value);
	}
	
	/**
	 * {@link Properties#getProperty(String)}
	 */
	public String getProp(PropertiesKey key) {
		return key != null ? prop.getProperty(key.toString()) : null;
	}
	
	/**
	 * {@link Properties#getProperty(String)}
	 * @param key
	 * @param value
	 */
	public void setIntProp(PropertiesKey key, int value) {
		final String strValue = String.valueOf(value);
		if (key == null || strValue == null)
			return;
		
		setProp(key, strValue);
	}
	
	/**
	 * get a value and parse it to an integer
	 * @param key
	 * @return a parsed number from the key or 0
	 */
	public int getIntProp(PropertiesKey key) {
		final String value = getProp(key);
		return parseInt(value);
	}
	
	/**
	 * {@link Integer#parseInt(String)}
	 * @param value a number as string
	 * @return a parsed number from the input or 0
	 */
	public int parseInt(String value) {
		int result = -1;
		
		if (value != null && !value.trim().isEmpty()) {
			try {
				result = Integer.parseInt(value);
			} catch (final NumberFormatException e) {/* ignore */}
		}
		
		return result;
	}
	
	/**
	 * {@link Properties#remove(Object)}
	 * @param key
	 */
	protected void removeProp(PropertiesKey key) {
		if (key == null)
			return;
		
		prop.remove(key.toString());
	}
	
	/**
	 * {@link Properties#remove(Object)}
	 * @param key
	 */
	protected void removeProp(String key) {
		if (key == null)
			return;
		
		prop.remove(key);
	}
	
	/**
	 * {@link Properties#clear()}
	 */
	protected void clearProp() {
		prop.clear();
	}
	
	/**
	 * Returns the Properties
	 */
	protected Properties getProperties() {
		return prop;
	}
	
	/**
	 * set the timezone to utc
	 */
	protected void setTimezoneUTC() {
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
}
