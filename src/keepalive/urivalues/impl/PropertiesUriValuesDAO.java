package keepalive.urivalues.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import freenet.keys.FreenetURI;
import keepalive.Plugin;
import keepalive.exceptions.DAOException;
import keepalive.model.Block;
import keepalive.model.IBlock;
import keepalive.model.PropertiesKey;
import keepalive.urivalues.IUriValue;
import keepalive.urivalues.IUriValuesDAO;

/**
 * Implementation of {@link IUriValuesDAO}<br>
 * The uri values get managed and persisted with the plugin properties
 */
public class PropertiesUriValuesDAO implements IUriValuesDAO {
	
	private final Plugin plugin;
	private final Properties prop;
	
	public PropertiesUriValuesDAO(Plugin plugin, Properties prop) {
		this.plugin = plugin;
		this.prop = prop;
	}
	
	@Override
	public IUriValue create(FreenetURI uri) throws DAOException {
		if (uri == null)
			throw new DAOException("The uri need to be not null!");
		if (existUri(uri))
			throw new DAOException("The uri: '%s' is already saved", uri);
		
		final IUriValue uriProps = new UriValue(getNewUriId(), uri);
		final int uriId = uriProps.getUriId();
		
		setProp(PropertiesKey.URI, uriId, uriProps.getUri().toString());
		setIntProp(PropertiesKey.BLOCKS, uriId, uriProps.getBlockCount());
		setProp(PropertiesKey.SUCCESS, uriId, uriProps.getSuccess());
		setIntProp(PropertiesKey.SEGMENT, uriId, uriProps.getSegment());
		
		plugin.setProp(PropertiesKey.IDS, plugin.getProp(PropertiesKey.IDS) + uriProps.getUriId() + ",");
		
		plugin.saveProp();
		return uriProps;
	}
	
	@Override
	public UriValue read(int uriId) throws DAOException {
		if (!existUriId(uriId))
			throw new DAOException("The uriId: '%s' doesnt exist", uriId);
		
		UriValue result = null;
		try {
			final UriValue uriValue = new UriValue(uriId);
			
			uriValue.setUri(new FreenetURI(getProp(PropertiesKey.URI, uriId)));
			uriValue.setBlockCount(getIntProp(PropertiesKey.BLOCKS, uriId));
			loadBlockUris(uriValue);
			uriValue.setSuccessSegments(getProp(PropertiesKey.SUCCESS_SEGMENTS, uriId));
			uriValue.setSuccess(getProp(PropertiesKey.SUCCESS, uriId));
			uriValue.setHistory(getProp(PropertiesKey.HISTORY, uriId));
			uriValue.setSegment(getIntProp(PropertiesKey.SEGMENT, uriId));
			
			result = uriValue;
		} catch (final MalformedURLException e) {
			throw new DAOException("The uriId: '%s' couldnt be loaded, because the uri seams broken", e, uriId);
		}
		
		return result;
	}
	
	@Override
	public void update(IUriValue uriValue) throws DAOException {
		if (uriValue == null)
			throw new DAOException("The uriValue need to be not null!");
		if (!exist(uriValue.getUri()))
			throw new DAOException("The uri: '%s' doesnt exist", uriValue.getUri());
		
		final int uriId = uriValue.getUriId();
		setProp(PropertiesKey.URI, uriId, uriValue.getUri().toString());
		saveBlockUris(uriValue);
		setIntProp(PropertiesKey.BLOCKS, uriId, uriValue.getBlocks().size() > 0 ? uriValue.getBlocks().size() : uriValue.getBlockCount());
		setProp(PropertiesKey.SUCCESS_SEGMENTS, uriId, uriValue.getSuccessSegments());
		setProp(PropertiesKey.SUCCESS, uriId, uriValue.getSuccess());
		setProp(PropertiesKey.HISTORY, uriId, uriValue.getHistory());
		setIntProp(PropertiesKey.SEGMENT, uriId, uriValue.getSegment());
		
		plugin.saveProp();
	}
	
	@Override
	public void delete(int uriId) throws DAOException {
		if (!existUriId(uriId))
			throw new DAOException("The uriId: '%s' doesnt exist", uriId);
		
		removeProp(PropertiesKey.URI, uriId);
		removeProp(PropertiesKey.BLOCKS, uriId);
		removeProp(PropertiesKey.SUCCESS, uriId);
		removeProp(PropertiesKey.SUCCESS_SEGMENTS, uriId);
		removeProp(PropertiesKey.SEGMENT, uriId);
		removeProp(PropertiesKey.HISTORY, uriId);
		
		// remove key files
		final File file = new File(plugin.getPluginDirectory() + getBlockListFilename(uriId));
		if (file.exists() && !file.delete()) {
			plugin.log("delete(): remove key files was not successful.", 1);
		}
		
		final String ids = ("," + plugin.getProp(PropertiesKey.IDS)).replaceAll("," + uriId + ",", ",");
		plugin.setProp(PropertiesKey.IDS, ids.substring(1));
		
		plugin.saveProp();
	}
	
	@Override
	public boolean exist(FreenetURI freenetUri) throws DAOException {
		if (freenetUri == null)
			throw new DAOException("The freenetUri need to be not null!");
		
		return existUri(freenetUri);
	}
	
	@Override
	public List<IUriValue> getAll() throws DAOException {
		return getAllUriIds().parallelStream()
				.map(this::readUnchecked)
				.filter(x -> x != null && x.getUri() != null)
				.collect(Collectors.toList());
	}
	
	private boolean existUri(FreenetURI uri) throws DAOException {
		if (uri == null)
			throw new DAOException("The uri need to be not null!");
		
		return getAllUriIds().stream().map(x -> getProp(PropertiesKey.URI, x)).anyMatch(x -> uri.toString().equalsIgnoreCase(x));
	}
	
	private boolean existUriId(int uriId) throws DAOException {
		return getAllUriIds().stream().anyMatch(x -> x.equals(uriId));
	}
	
	private List<Integer> getAllUriIds() throws DAOException {
		List<Integer> result = new ArrayList<>();
		
		try {
			final String uriIds = plugin.getProp(PropertiesKey.IDS);
			if (uriIds == null || uriIds.trim().isEmpty())
				return result;
			
			result = Arrays.asList(uriIds.split(",")).stream().map(Integer::parseInt).collect(Collectors.toList());
		} catch (final NumberFormatException e) {
			throw new DAOException("There is a problen with the id parsing", e);
		}
		
		return result;
	}
	
	/**
	 * Returns the highest free id or 0
	 * @throws DAOException
	 */
	private int getNewUriId() throws DAOException {
		return getAllUriIds().stream().mapToInt(x -> x).max().orElse(-1) + 1;
	}
	
	/**
	 * Throws no exception for use in streams
	 */
	private UriValue readUnchecked(int uriId) {
		try {
			return read(uriId);
		} catch (final Exception e) {
			plugin.log("Error on readUnchecked with uriId: %s", e, uriId);
			return null;
		}
	}
	
	private void saveBlockUris(IUriValue uriValue) throws DAOException {
		final Path fileName = Paths.get(plugin.getPluginDirectory(), getBlockListFilename(uriValue.getUriId()));
		final List<String> content = uriValue.getBlocks().values().stream().map(this::convertLine).collect(Collectors.toList());
		
		try {
			final OpenOption openOption = Files.exists(fileName) ? StandardOpenOption.WRITE : StandardOpenOption.CREATE;
			Files.write(fileName, content, StandardCharsets.UTF_8, openOption, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (final Exception e) {
			throw new DAOException("Error saving blocks for: %s", e, uriValue.getUri());
		}
	}
	
	private void loadBlockUris(IUriValue uriValue) throws DAOException {
		final Path fileName = Paths.get(plugin.getPluginDirectory(), getBlockListFilename(uriValue.getUriId()));
		if (!Files.exists(fileName))
			return;
		
		try {
			Files.readAllLines(fileName, StandardCharsets.UTF_8).stream()
					.filter(x -> x != null && !x.trim().isEmpty() && x.contains("#"))
					.map(this::parseLine)
					.filter(x -> x != null)
					.forEach(x -> uriValue.getBlocks().put(x.getUri(), x));
		} catch (final Exception e) {
			throw new DAOException("Error loading blocks for: %s", e, uriValue.getUri());
		}
	}
	
	private String convertLine(IBlock block) {
		final String type = block.isDataBlock() ? "d" : "c";
		final String msg = String.format("%s#%s#%s#%s", block.getUri(), block.getSegmentId(), block.getId(), type);
		if (msg.startsWith("HK"))
			plugin.logF("convertLine: %s | %s", block.getUri(), msg);
		return msg;
	}
	
	private Block parseLine(String fileLine) {
		try {
			final String[] values = fileLine.split("#", 4);
			return new Block(new FreenetURI(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), "d".equals(values[3]));
		} catch (NumberFormatException | MalformedURLException e) {
			plugin.log("parseLine error: %s", e, fileLine);
			return null;
		}
	}
	
	private String getBlockListFilename(int uriId) {
		return String.format("keys%s.txt", uriId);
	}
	
	/**
	 * special use for properties for a specific id
	 * {@link Properties#getProperty(String)}
	 */
	private String getProp(PropertiesKey key, int id) {
		return key != null ? prop.getProperty(key.toString() + "_" + id) : null;
	}
	
	/**
	 * special use for properties for a specific id
	 * {@link Properties#setProperty(String, String)}
	 */
	private void setProp(PropertiesKey key, int id, String value) {
		if (key == null || value == null)
			return;
		
		prop.setProperty(key.toString() + "_" + id, value);
	}
	
	/**
	 * special use for properties for a specific id
	 * {@link Properties#remove(Object)}
	 */
	private void removeProp(PropertiesKey key, int id) {
		if (key == null)
			return;
		
		prop.remove(key.toString() + "_" + id);
	}
	
	/**
	 * get a value and parse it to an integer<br>
	 * special use for properties for a specific id
	 */
	private int getIntProp(PropertiesKey key, int id) {
		final String strValue = getProp(key, id);
		return plugin.parseInt(strValue);
	}
	
	/**
	 * special use for properties for a specific id
	 * {@link Properties#getProperty(String)}
	 */
	private void setIntProp(PropertiesKey key, int id, int value) {
		final String strValue = String.valueOf(value);
		if (key == null || strValue == null)
			return;
		
		setProp(key, id, strValue);
	}
	
}
