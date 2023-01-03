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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.NodeL10n;
import freenet.pluginmanager.FredPluginL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import keepalive.model.PropertiesKey;
import keepalive.model.UiKey;
import pluginbase.de.todesbaum.util.freenet.fcp2.Message;

public abstract class PageBase extends Toadlet implements FredPluginL10n {
	
	public PluginBase plugin;
	
	protected FcpCommands fcp;
	
	private PageNode page;
	private final List<HTMLNode> vBoxes = new ArrayList<>();
	private final Map<String, Message> mMessages = new TreeMap<>();
	private String strPageName;
	private String strPageTitle;
	private String strRefreshTarget;
	private int nRefreshPeriod = -1;
	private URI uri;
	private HTTPRequest httpRequest;
	private final TreeMap<String, String> mRedirectURIs = new TreeMap<>();
	private String strRedirectURI;
	private boolean bFullAccessHostsOnly;
	
	protected PageBase(String cPageName, String cPageTitle, PluginBase plugin, boolean bFullAccessHostsOnly) {
		super(plugin.pluginContext.node.clientCore.makeClient((short) 3, false, false));
		
		try {
			this.strPageName = cPageName;
			this.strPageTitle = cPageTitle;
			this.plugin = plugin;
			this.bFullAccessHostsOnly = bFullAccessHostsOnly;
			
			// register this page and add to menu
			plugin.webInterface.registerInvisible(this);
			plugin.log("page '" + cPageName + "' registered");
			
			// fcp request object
			fcp = new FcpCommands(plugin.fcpConnection, this);
			
		} catch (final Exception e) {
			plugin.log("PageBase(): " + e.getMessage(), 1);
		}
	}
	
	public String getName() {
		return strPageName;
	}
	
	@Override
	public String path() {
		return plugin.getPath() + "/" + strPageName;
	}
	
	/**
	 *
	 * @param cKey
	 * @return
	 */
	@Override
	public String getString(String cKey) {       // FredPluginL10n
		return plugin.getString(cKey);
	}
	
	/**
	 *
	 * @param newLanguage
	 */
	@Override
	public void setLanguage(LANGUAGE newLanguage) {      // FredPluginL10n
		plugin.setLanguage(newLanguage);
	}
	
	@Override
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		try {
			
			vBoxes.clear();
			if (!bFullAccessHostsOnly || ctx.isAllowedFullAccess()) {
				this.uri = uri;
				this.httpRequest = request;
				handleRequest();
			} else {
				addBox("Access denied!", "Access to this page for hosts with full access rights only.", null);
			}
			
			makePage(uri, ctx);
			
		} catch (final Exception e) {
			log("PageBase.handleMethodGET(): " + e.getMessage(), 1);
		}
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		handleMethodGET(uri, request, ctx);
	}
	
	@Override
	public boolean allowPOSTWithoutPassword() {
		return true;
	}
	
	private String getIdentifier(Message message) throws Exception {
		try {
			
			final String[] aIdentifier = message.getIdentifier().split("_");
			final StringBuilder cIdentifier = new StringBuilder().append(aIdentifier[1]);
			for (int i = 2; i < aIdentifier.length - 1; i++) {
				cIdentifier.append("_").append(aIdentifier[i]);
			}
			return cIdentifier.toString();
			
		} catch (final Exception e) {
			throw new Exception("PageBase.getIdentifier(): " + e.getMessage());
		}
	}
	
	void addMessage(Message message) throws Exception {
		try {
			
			// existing message with same id becomes replaced (e.g. AllData replaces DataFound)
			mMessages.put(getIdentifier(message), message);
			
		} catch (final Exception e) {
			throw new Exception("PageBase.addMessage(): " + e.getMessage());
		}
	}
	
	void updateRedirectUri(Message message) throws Exception {
		try {
			
			// existing RedirectUri with same id becomes replaced
			mRedirectURIs.put(getIdentifier(message), message.get("RedirectUri"));
			
		} catch (final Exception e) {
			throw new Exception("PageBase.updateRedirectUri(): " + e.getMessage());
		}
	}
	
	private void makePage(URI uri, ToadletContext ctx) throws Exception {
		try {
			
			String path = uri.getPath().substring(plugin.getPath().length());
			if (path.startsWith("/static/")) {
				path = "/resources" + path;
				try (InputStream inputStream = getClass()
						.getResourceAsStream(path)) {
					if (inputStream == null) {
						this.sendErrorPage(ctx, 404,
								NodeL10n.getBase().getString("StaticToadlet.pathNotFoundTitle"),
								NodeL10n.getBase().getString("StaticToadlet.pathNotFound"));
						return;
					}
					
					final String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
					
					final ByteArrayOutputStream content = new ByteArrayOutputStream();
					int len;
					final byte[] contentBytes = new byte[1024];
					while ((len = inputStream.read(contentBytes)) != -1) {
						content.write(contentBytes, 0, len);
					}
					
					writeReply(ctx, 200, mimeType, "", content.toByteArray(), 0, content.size());
					return;
				}
			}
			
			page = plugin.pagemaker.getPageNode(strPageTitle, ctx);
			
			// refresh page
			if (nRefreshPeriod != -1) {
				if (strRefreshTarget == null) {
					strRefreshTarget = uri.getPath();
					if (uri.getQuery() != null) {
						strRefreshTarget += "?" + uri.getQuery();
					}
				}
				page.headNode.addChild("meta", new String[] { "http-equiv", "content" },
						new String[] { "refresh", nRefreshPeriod + ";URL=" + strRefreshTarget });
			}
			
			page.headNode.addChild("link",
					new String[] { "rel", "href", "type" },
					new String[] { "stylesheet", "static/style.css", "text/css" });
			
			// boxes
			for (final HTMLNode box : vBoxes) {
				page.content.addChild(box);
			}
			
			// write
			writeHTMLReply(ctx, 200, "OK", page.outer.generate());
			
		} catch (ToadletContextClosedException | IOException e) {
			throw new Exception("PageBase.html(): " + e.getMessage());
		}
	}
	
	// ********************************************
	// methods to use in the derived plugin class:
	// ********************************************
	// file log
	public void log(String strText, int nLogLevel) {
		plugin.log(strText, nLogLevel);
	}
	
	public void log(String strText) {
		plugin.log(strText);
	}
	
	public void log(String strText, Object... args) {
		plugin.logF(strText, args);
	}
	
	// methods to add this page to the plugins' menu (fproxy)
	protected void addPageToMenu(String strMenuTitle, String strMenuTooltip) {
		try {
			
			plugin.pluginContext.pluginRespirator.getToadletContainer().unregister(this);
			plugin.pluginContext.pluginRespirator.getToadletContainer().register(this, plugin.getCategory(),
					this.path(), true, strMenuTitle, strMenuTooltip, bFullAccessHostsOnly, null);
			log("page '" + strPageName + "' added to menu");
			
		} catch (final Exception e) {
			log("PageBase.addPageToMenu(): " + e.getMessage(), 1);
		}
	}
	
	// methods to build the page
	protected void addBox(String title, String htmlBody, String id) {
		try {
			final InfoboxNode box = plugin.pagemaker.getInfobox(title);
			if (id != null) {
				box.outer.addAttribute("id", id);
			}
			htmlBody = htmlBody.replace('\'', '"');
			box.content.addChild("%", htmlBody);
			vBoxes.add(box.outer);
			
		} catch (final Exception e) {
			log("PluginBase.addBox(): " + e.getMessage(), 1);
		}
	}
	
	protected String html(String name, String formPassword) throws Exception {
		try (InputStream stream = getClass()
				.getResourceAsStream("/resources/templates/" + name + ".html")) {
			
			final ByteArrayOutputStream content = new ByteArrayOutputStream();
			int len;
			final byte[] contentBytes = new byte[1024];
			while ((len = stream.read(contentBytes)) != -1) {
				content.write(contentBytes, 0, len);
			}
			
			return content.toString(StandardCharsets.UTF_8.toString()).replace("${formPassword}", formPassword);
		} catch (final IOException e) {
			throw new Exception("PageBase.html(): " + e.getMessage());
		}
	}
	
	// methods to make the page refresh
	protected void setRefresh(int nPeriod, String strTarget) {
		this.nRefreshPeriod = nPeriod;
		this.strRefreshTarget = strTarget;
	}
	
	protected void setRefresh(int nPeriod) {
		setRefresh(nPeriod, null);
	}
	
	// methods to handle http requests (both get and post)
	abstract protected void handleRequest();
	
	protected String getQuery() throws Exception {
		try {
			
			return uri.getQuery();
			
		} catch (final Exception e) {
			throw new Exception("PageBase.getQuery(): " + e.getMessage());
		}
	}
	
	protected HTTPRequest getRequest() {
		return httpRequest;
	}
	
	protected boolean isParamSet(UiKey propKey) throws Exception {
		return getParam(propKey.toString()) != null;
	}
	
	protected boolean isParamSet(PropertiesKey propKey) throws Exception {
		return getParam(propKey.toString()) != null;
	}
	
	protected String getParam(UiKey propKey) throws Exception {
		return getParam(propKey.toString());
	}
	
	protected String getParam(String strKey) throws Exception {
		if (strKey == null)
			return null;
		
		try {
			if ("GET".equalsIgnoreCase(httpRequest.getMethod()) && !"".equals(httpRequest.getParam(strKey))) {
				return httpRequest.getParam(strKey);
			}
			if ("POST".equalsIgnoreCase(httpRequest.getMethod()) && httpRequest.getPart(strKey) != null) {
				final byte[] aContent = new byte[(int) httpRequest.getPart(strKey).size()];
				httpRequest.getPart(strKey).getInputStream().read(aContent);
				return new String(aContent, UTF_8);
			}
			
			return null;
		} catch (final IOException e) {
			throw new Exception("PageBase.getParam(): " + e.getMessage());
		}
	}
	
	protected int getIntParam(UiKey key) throws Exception {
		try {
			return Integer.parseInt(getParam(key));
		} catch (final Exception e) {
			throw new Exception("PageBase.getIntParam(): " + e.getMessage());
		}
	}
	
	protected int getIntParam(PropertiesKey key) throws Exception {
		try {
			return Integer.parseInt(getParam(key.toString()));
		} catch (final Exception e) {
			throw new Exception("PageBase.getIntParam(): " + e.getMessage());
		}
	}
	
	protected int getIntParam2(String key) throws Exception {
		try {
			return Integer.parseInt(getParam(key));
		} catch (final Exception e) {
			throw new Exception("PageBase.getIntParam(): " + e.getMessage());
		}
	}
	
	// methods to handle fcp messages
	protected Message getMessage(String cId, String cMessageType) throws Exception {
		try {
			
			Message message = mMessages.get(cId);
			if (message != null && !message.getName().equals(cMessageType)) {
				message = null;
			}
			if (message != null) {
				mMessages.remove(cId);
				strRedirectURI = mRedirectURIs.get(cId);
				mRedirectURIs.remove(cId);
			}
			return message;                 // returns null if no message
			
		} catch (final Exception e) {
			throw new Exception("PageBase.getMessage(): " + e.getMessage());
		}
	}
	
	protected String getRedirectURI() {
		return strRedirectURI;
	}
	
	protected String[] getSSKKeypair(String cId) {
		try {
			
			final Message message = getMessage(cId, "SSKKeypair");
			if (message != null) {
				return new String[] { message.get("InsertURI"), message.get("RequestURI") };
			}
			return null;
			
		} catch (final Exception e) {
			log("PageBase.getSSKKeypair(): " + e.getMessage(), 1);
			return null;
		}
	}
	
	// methods to set and get persistent properties
	protected void saveProp() {
		plugin.saveProp();
	}
	
	protected void setProp(PropertiesKey key, String value) {
		plugin.setProp(key, value);
	}
	
	protected String getProp(PropertiesKey key) {
		return plugin.getProp(key);
	}
	
	protected void setIntProp(PropertiesKey key, int nValue) {
		plugin.setIntProp(key, nValue);
	}
	
	protected int getIntProp(PropertiesKey key) {
		return plugin.getIntProp(key);
	}
	
	protected void removeProp(PropertiesKey key) {
		plugin.removeProp(key);
	}
	
	// method to allow access to this page for full-access-hosts only
	public void restrictToFullAccessHosts(boolean bRestrict) {
		bFullAccessHostsOnly = bRestrict;
	}
	
}
