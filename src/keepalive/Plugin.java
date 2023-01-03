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
package keepalive;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import freenet.client.HighLevelSimpleClientImpl;
import freenet.pluginmanager.PluginRespirator;
import keepalive.exceptions.DAOException;
import keepalive.model.PropertiesKey;
import keepalive.model.SuccessValues;
import keepalive.repository.IDatabaseDAO;
import keepalive.repository.impl.H2DatabaseDAO;
import keepalive.service.reinserter.Reinserter;
import keepalive.urivalues.IUriValue;
import keepalive.urivalues.IUriValuesDAO;
import keepalive.urivalues.impl.PropertiesUriValuesDAO;
import keepalive.web.AdminPage;
import pluginbase.PluginBase;

/**
 * Mainclass and Startpoint
 */
public class Plugin extends PluginBase {
	
	public static final String VERSION = "0.3.4.0-PlantEater";
	public static final String PLUGIN_NAME = "KeepAlive";
	
	private Thread reinserterRunner;
	private long propSavingTimestamp;
	private HighLevelSimpleClientImpl hlsc;
	public IDatabaseDAO databaseDAO;
	public IUriValuesDAO uriPropsDAO;
	
	public Plugin() {
		super(PLUGIN_NAME, PLUGIN_NAME, "prop.txt");
		
		setVersion(VERSION);
		addPluginToMenu(PLUGIN_NAME, "Reinsert sites and files in the background");
		clearLog();
		
		this.databaseDAO = new H2DatabaseDAO(this);
		this.uriPropsDAO = new PropertiesUriValuesDAO(this, getProperties());
	}
	
	@Override
	public void runPlugin(PluginRespirator pr) {
		super.runPlugin(pr);
		
		try {
			hlsc = (HighLevelSimpleClientImpl) pluginContext.node.clientCore.makeClient((short) 5, false, true);
			
			// initialize all common property keys with its default value
			initPropKeys();
			
			updateProperties();
			
			// db migration
			this.databaseDAO.pluginStart();
			
			// initial values
			if (getIntProp(PropertiesKey.LOG_UTC) == 1)
				setTimezoneUTC();
			
			// build page and menu
			addPage(new AdminPage(this, pluginContext.node.clientCore.formPassword));
			addMenuItem("Documentation", "Go to the documentation site",
					"/USK@l9wlbjlCA7kfcqzpBsrGtLoAB4-Ro3vZ6q2p9bQ~5es,bGAKUAFF8UryI04sxBKnIQSJWTSa08BDS-8jmVQdE4o,AQACAAE/keepalive/15", true);
			
			// start reinserter
			final int activeProp = getIntProp(PropertiesKey.ACTIVE);
			if (activeProp != -1) {
				startReinserter(activeProp);
			}
		} catch (final Exception e) {
			log("Plugin.runPlugin Exception: " + e.getMessage(), 0);
		}
	}
	
	private void updateProperties() throws DAOException {
		// migrate from 0.2 to 0.3
		if (getProp(PropertiesKey.VERSION) != null && "0.2".equals(getProp(PropertiesKey.VERSION).substring(0, 3))) {
			for (final IUriValue uriValue : uriPropsDAO.getAll()) {
				// remove boost params
				removeProp("boost_" + uriValue.getUriId());
				
				// empty all block list
				uriValue.setBlockCount(-1);
				uriPropsDAO.update(uriValue);
			}
			
			setProp(PropertiesKey.VERSION, VERSION);
			saveProp(true);
		}
		
		// rewrite update
		if ("0.3.3.11-RW".equals(getProp(PropertiesKey.VERSION))) {
			setProp(PropertiesKey.DB_VERSION, "199");
			setProp(PropertiesKey.VERSION, VERSION);
			saveProp(true);
			return;
		}
		
		// new install
		if (getProp(PropertiesKey.VERSION) == null) {
			setProp(PropertiesKey.DB_VERSION, "206");
			setProp(PropertiesKey.VERSION, VERSION);
			saveProp(true);
		}
	}
	
	public void startReinserter(final int siteId) {
		try {
			// stop previous reinserter
			stopReinserter();
			
			setIntProp(PropertiesKey.ACTIVE, siteId);
			saveProp();
			
			// start this one
			synchronized (this) {
				final Plugin plugin = this;
				reinserterRunner = new Thread(() -> {
					int startId = siteId;
					final Thread thread = Thread.currentThread();
					
					while (true) {
						try {
							for (final IUriValue uriValue : uriPropsDAO.getAll()) {
								if (thread.isInterrupted())
									return;
								
								// find start
								if (startId != -1) {
									if (uriValue.getUriId() != startId)
										continue;
									startId = -1;
								}
								
								setIntProp(PropertiesKey.ACTIVE, uriValue.getUriId());
								saveProp();
								
								final CountDownLatch latch = new CountDownLatch(1);
								final Reinserter reinserter = new Reinserter(plugin, uriValue, latch);
								reinserter.start();
								try {
									if (!latch.await(getIntProp(PropertiesKey.SINGLE_URL_TIMESLOT), TimeUnit.HOURS)) {
										reinserter.interrupt();
										log("Terminated reinserter " + uriValue.getUriId() + " by timeout");
									}
								} catch (final InterruptedException e) {
									thread.interrupt();
									reinserter.interrupt();
									return;
								}
							}
						} catch (final DAOException e) {
							plugin.log("Problem with a DAO", e);
							stopReinserter();
							break;
						}
					}
				});
				reinserterRunner.setName(PLUGIN_NAME + " Reinserter Runner");
				reinserterRunner.start();
			}
		} catch (final Exception e) {
			log("Plugin.startReinserter Exception: " + e.getMessage(), 0);
		}
	}
	
	public synchronized void stopReinserter() {
		try {
			if (reinserterRunner != null) {
				reinserterRunner.interrupt();
				setIntProp(PropertiesKey.ACTIVE, -1);
				saveProp();
			}
		} catch (final Exception e) {
			log("Plugin.stopReinserter Exception: " + e.getMessage(), 0);
		}
	}
	
	public SuccessValues getSuccessValues(IUriValue uriValue) {
		SuccessValues result = new SuccessValues(0, 0, 0);
		
		try {
			// available blocks
			int success = 0;
			int failed = 0;
			final String[] successMap = uriValue.getSuccess().split(",");
			if (successMap.length >= 2) {
				for (int i = 0; i < successMap.length; i += 2) {
					success += Integer.parseInt(successMap[i]);
					failed += Integer.parseInt(successMap[i + 1]);
				}
			}
			
			// available segments
			int availableSegments = 0;
			final String successSegments = uriValue.getSuccessSegments();
			final int lastTriedSegment = uriValue.getSegment();
			if (successSegments != null) {
				if (lastTriedSegment >= successSegments.length()) {
					log("Plugin.getSuccessValues(): List of success_segments too short for siteId " +
							uriValue.getUriId() + "! " + successSegments.length() + " vs " + lastTriedSegment + 1, 0);
				}
				
				for (int i = 0; i <= lastTriedSegment && i < successSegments.length(); i++) {
					if (successSegments.charAt(i) == '1') {
						availableSegments++;
					}
				}
			}
			
			result = new SuccessValues(success, failed, availableSegments);
		} catch (final Exception e) {
			log("Plugin.getSuccessValues Exception: " + e.getMessage(), 0);
		}
		
		return result;
	}
	
	public String getLogFilename(IUriValue uriValue) {
		return String.format("log%s.txt", uriValue.getUriId());
	}
	
	public synchronized void saveProp(boolean force) {
		if (force || propSavingTimestamp < System.currentTimeMillis() - 10 * 1000) {
			super.saveProp();
			propSavingTimestamp = System.currentTimeMillis();
		}
	}
	
	@Override
	public void saveProp() {
		saveProp(false);
	}
	
	@Override
	public void terminate() {
		stopReinserter();
		
		databaseDAO.pluginTerminate();
		
		super.terminate();
		log("plugin terminated", 0);
	}
	
	public HighLevelSimpleClientImpl getFreenetClient() {
		return hlsc;
	}
	
	public void removeUriAndFiles(IUriValue uriValue) {
		// stop reinserter
		if (uriValue.getUriId() == getIntProp(PropertiesKey.ACTIVE)) {
			stopReinserter();
		}
		
		// remove log files
		try {
			final File file = new File(getPluginDirectory(), getLogFilename(uriValue));
			Files.deleteIfExists(file.toPath());
		} catch (final Exception e) {
			log("Plugin.removeUriAndFiles(): remove log files was not successful.", e);
			return;
		}
		
		try {
			// remove top block from db
			databaseDAO.delete(uriValue.getUri());
			
			// remove items
			uriPropsDAO.delete(uriValue.getUriId());
		} catch (final DAOException e) {
			log("Plugin.removeUriAndFiles(): Can't delete uriValues", e);
		}
	}
	
	/**
	 * initialize all common property keys with its default value
	 */
	private void initPropKeys() {
		for (final PropertiesKey key : PropertiesKey.values())
			initPropKey(key);
		
		saveProp(true);
	}
	
	/**
	 * initialize a property key with its default value
	 */
	private void initPropKey(PropertiesKey key) {
		if (getProp(key) == null) {
			final Object defaultValue = key.getDefault();
			
			if (defaultValue == null) {
				// value without default ignore
			} else if (defaultValue instanceof String) {
				setProp(key, (String) defaultValue);
			} else if (defaultValue instanceof Integer) {
				setIntProp(key, (Integer) defaultValue);
			} else {
				log(String.format("Unknown default value: '%s' for key: '%s'", defaultValue, key));
			}
		}
	}
	
}
