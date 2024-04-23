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
package keepalive.web;

import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import freenet.keys.FreenetURI;
import keepalive.Plugin;
import keepalive.exceptions.DAOException;
import keepalive.model.PropertiesKey;
import keepalive.model.SuccessValues;
import keepalive.model.UiKey;
import keepalive.urivalues.IUriValue;
import pluginbase.PageBase;

public class AdminPage extends PageBase {
	
	private final Plugin ownPlugin;
	private final String formPassword;
	private static final String FORM_PASS = "&formPassword=";
	
	public AdminPage(Plugin plugin, String formPassword) {
		super("", "Keep Alive", plugin, true);
		this.ownPlugin = plugin;
		this.formPassword = formPassword;
		addPageToMenu("Start reinsertion of sites", "Add or remove sites you like to reinsert");
	}
	
	@Override
	protected void handleRequest() {
		try {
			if (formPassword.equals(getParam(UiKey.FORMPASSWORD))) {
				// start reinserter
				if (isParamSet(UiKey.START)) {
					ownPlugin.startReinserter(getIntParam(UiKey.START));
				}
				
				// stop reinserter
				if (isParamSet(UiKey.STOP)) {
					ownPlugin.stopReinserter();
				}
				
				// modify power
				if (isParamSet(PropertiesKey.POWER)) {
					setIntPropByParam(PropertiesKey.POWER, 1);
					saveProp();
				}
				
				// modify splitfile tolerance
				if (isParamSet(PropertiesKey.SPLITFILE_TOLERANCE)) {
					setIntPropByParam(PropertiesKey.SPLITFILE_TOLERANCE, 0);
					saveProp();
				}
				
				// modify splitfile tolerance
				if (isParamSet(PropertiesKey.SPLITFILE_TEST_SIZE)) {
					setIntPropByParam(PropertiesKey.SPLITFILE_TEST_SIZE, 10);
					saveProp();
				}
				
				// modify timeslot to heal single url
				if (isParamSet(PropertiesKey.SINGLE_URL_TIMESLOT)) {
					setIntPropByParam(PropertiesKey.SINGLE_URL_TIMESLOT, 1);
					saveProp();
				}
				
				// modify log level
				if (isParamSet(UiKey.MODIFY_LOGLEVEL) || isParamSet(UiKey.SHOW_LOG)) {
					setIntPropByParam(PropertiesKey.LOGLEVEL, 0);
					saveProp();
				}
				
				// clear logs
				if (isParamSet(UiKey.CLEAR_LOGS)) {
					ownPlugin.clearAllLogs();
				}
				
				// clear history
				if (isParamSet(UiKey.CLEAR_HISTORY)) {
					clearHistory();
				}
				
				// add uris
				if (isParamSet(UiKey.URIS)) {
					addUris();
				}
				
				// remove uri
				if (isParamSet(UiKey.REMOVE)) {
					removeUri();
				}
				
				// remove regex
				if (isParamSet(UiKey.REMOVE_WITH_REGEX)) {
					removeWithRegex();
				}
				
				// remove all
				if (isParamSet(UiKey.REMOVE_ALL)) {
					removeAllUris();
				}
			}
			
			// refresh boxes
			final List<IUriValue> uriValues = ownPlugin.uriPropsDAO.getAll();
			unsupportedKeysBox(uriValues);
			sitesBox(uriValues);
			logBox();
			configurationBox();
			historyBox(uriValues);
			
			// info box
			addBox("Information", html("info", formPassword).replace("#1", ownPlugin.getVersion()), "page-kp-info");
			
		} catch (final Exception e) {
			log("AdminPage.handleRequest(): " + e.getMessage());
		}
	}
	
	private void clearHistory() throws Exception {
		int value = -1;
		try {
			value = Integer.parseInt(getParam(UiKey.CLEAR_HISTORY));
		} catch (final NumberFormatException e) {/* ignore */}
		
		final IUriValue uriValue = ownPlugin.uriPropsDAO.read(value);
		if (uriValue != null) {
			uriValue.setHistory(null);
			ownPlugin.uriPropsDAO.update(uriValue);
		}
	}
	
	private void historyBox(List<IUriValue> uriValues) {
		final StringBuilder html = new StringBuilder("<table>");
		
		for (final IUriValue uriValue : uriValues) {
			html.append("<tr><td>")
					.append(uriValue.getShortUri())
					.append("</td><td>");
			
			final String history = uriValue.getHistory();
			if (history != null && !history.trim().isEmpty()) {
				html.append(history.replace('-', '=').replace(",", "%, "))
						.append("%");
			}
			
			html.append("</td><td><a href=\"?clear_history=")
					.append(uriValue.getUriId())
					.append(FORM_PASS)
					.append(formPassword)
					.append("\">clear</a></td></tr>");
		}
		html.append("</table>");
		
		addBox("Lowest rate of blocks availability (monthly)", html.toString(), "page-kp-rate");
	}
	
	private void configurationBox() throws Exception {
		StringBuilder html = new StringBuilder(html("properties", formPassword));
		html = new StringBuilder(html.toString().replace("#1", getProp(PropertiesKey.POWER)));
		html = new StringBuilder(html.toString().replace("#2", getProp(PropertiesKey.LOGLEVEL)));
		html = new StringBuilder(html.toString().replace("#3", getProp(PropertiesKey.SPLITFILE_TOLERANCE)));
		html = new StringBuilder(html.toString().replace("#4", getProp(PropertiesKey.SPLITFILE_TEST_SIZE)));
		html = new StringBuilder(html.toString().replace("#5", getProp(PropertiesKey.SINGLE_URL_TIMESLOT)));
		addBox("Configuration", html.toString(), "page-kp-config");
	}
	
	private void logBox() throws Exception {
		if (isParamSet(UiKey.MASTER_LOG) || isParamSet(UiKey.LOG)) {
			String log;
			IUriValue uriValue = null;
			if (isParamSet(UiKey.MASTER_LOG)) {
				log = ownPlugin.getLog();
			} else {
				uriValue = ownPlugin.uriPropsDAO.read(getIntParam(UiKey.LOG));
				log = ownPlugin.getLog(ownPlugin.getLogFilename(uriValue));
			}
			
			if (log == null)
				log = "";
			
			final StringBuilder html = new StringBuilder(
					("<small>" + log + "</small>")
							.replace("\n", "<br>")
							.replaceAll(" {2}", "&nbsp; &nbsp; "));
			
			if (isParamSet(UiKey.MASTER_LOG)) {
				addBox("Master log", html.toString(), "log_anchor");
			} else if (uriValue != null) {
				addBox("Log for " + uriValue.getShortUri(), html.toString(), "log_anchor");
			}
		}
	}
	
	private void sitesBox(List<IUriValue> uriValues) throws Exception {
		final StringBuilder html = new StringBuilder(html("add_key", formPassword)).append("<br>");
		
		final StringBuilder htmlEntries = new StringBuilder();
		for (final IUriValue uriValue : uriValues) {
			final String uri = uriValue.getUri().toString();
			
			final SuccessValues successValues = ownPlugin.getSuccessValues(uriValue);
			final int success = successValues.getSuccess();
			final int failure = successValues.getFailed();
			
			int persistence = 0;
			if (success > 0) {
				persistence = (int) ((double) success / (success + failure) * 100);
			}
			
			final int availableSegments = successValues.getAvailableSegments();
			final int finishedSegmentsCount = uriValue.getSegment() + 1;
			
			int segmentsAvailability = 0;
			if (finishedSegmentsCount > 0) {
				segmentsAvailability = (int) ((double) availableSegments / finishedSegmentsCount * 100);
			}
			
			final boolean isActive = uriValue.getUriId() == getIntProp(PropertiesKey.ACTIVE);
			final String entryHtml = html("url_entry", formPassword)
					.replace("${url_full}", uri)
					.replace("${url_short}", uriValue.getShortUri())
					.replace("${url_blockSize}", Integer.toString(uriValue.getBlocks().size()))
					.replace("${url_success}", Integer.toString(success))
					.replace("${url_failure}", Integer.toString(failure))
					.replace("${url_persistence}", Integer.toString(persistence))
					.replace("${url_segmentsAvailability}", Integer.toString(segmentsAvailability))
					.replace("${url_id}", Integer.toString(uriValue.getUriId()))
					.replace("${url_modus}", isActive ? "stop" : "start")
					.replace("${url_active}", isActive ? "active" : "");
			htmlEntries.append(entryHtml);
		}
		
		html.append(html("overview_table", formPassword).replace("${url_entries}", htmlEntries.toString()));
		
		addBox("Add or remove a key", html.toString(), "page-kp-keys");
	}
	
	private void unsupportedKeysBox(List<IUriValue> uriValues) throws Exception {
		final StringBuilder zeroBlockSites = new StringBuilder();
		
		for (final IUriValue uriValue : uriValues) {
			if (uriValue.getBlockCount() == 0) {
				if (zeroBlockSites.length() > 0)
					zeroBlockSites.append("<br>");
				
				zeroBlockSites.append(uriValue.getUri().toString());
			}
		}
		
		if (zeroBlockSites.length() > 0) {
			addBox("Unsupported keys", html("unsupported_keys", formPassword).replace("#", zeroBlockSites.toString()), null);
		}
	}
	
	private void addUris() throws Exception {
		final String uris = getParam(UiKey.URIS);
		if (uris == null || uris.trim().isEmpty())
			return;
		
		for (final String splitURI : uris.split("\n")) {
			// validate
			final String uriOrig = URLDecoder.decode(splitURI.trim(), StandardCharsets.UTF_8.toString()).trim();
			if (uriOrig.trim().isEmpty())
				continue; // ignore blank lines.
				
			try {
				final FreenetURI freenetUri = new FreenetURI(uriOrig);
				
				// add if not already on the list
				if (!isDuplicate(freenetUri)) {
					ownPlugin.uriPropsDAO.create(freenetUri);
				}
			} catch (final MalformedURLException e) {
				addBox("URI not valid!", "You have typed:<br><br>" + uriOrig, null);
			}
		}
		
		saveProp();
	}
	
	private void removeUri() throws Exception {
		final IUriValue uriValue = ownPlugin.uriPropsDAO.read(getIntParam(UiKey.REMOVE));
		if (uriValue != null)
			ownPlugin.removeUriAndFiles(uriValue);
		
		ownPlugin.saveProp(true);
	}
	
	private void removeWithRegex() throws Exception {
		final String regex = getParam(UiKey.REMOVE_REGEX);
		if (regex == null || regex.trim().isEmpty()) {
			log("regex value is empty");
			return;
		}
		
		for (final IUriValue uriValue : ownPlugin.uriPropsDAO.getAll()) {
			try {
				final String uri = uriValue.getUri().toString();
				if (uri.matches(regex)) {
					ownPlugin.removeUriAndFiles(uriValue);
				}
			} catch (final Exception e) {
				log("AdminPage.removeWithRegex(): " + e.getMessage());
			}
		}
		
		ownPlugin.saveProp(true);
	}
	
	private void removeAllUris() throws DAOException {
		for (final IUriValue uriValue : ownPlugin.uriPropsDAO.getAll())
			ownPlugin.removeUriAndFiles(uriValue);
		
		ownPlugin.saveProp(true);
	}
	
	private void setIntPropByParam(PropertiesKey cPropName, int nMinValue) {
		int value = nMinValue;
		
		try {
			value = getIntParam(cPropName);
		} catch (final Exception ex) {/* ignored */}
		
		if (value != -1 && value < nMinValue) {
			value = nMinValue;
		}
		
		setIntProp(cPropName, value);
		saveProp();
	}
	
	private boolean isDuplicate(FreenetURI freenetUri) throws DAOException {
		final boolean isDuplicate = ownPlugin.uriPropsDAO.exist(freenetUri);
		if (isDuplicate)
			addBox("Duplicate URI", "We are already keeping this key alive:<br><br>" + freenetUri, null);
		
		return isDuplicate;
	}
	
}
