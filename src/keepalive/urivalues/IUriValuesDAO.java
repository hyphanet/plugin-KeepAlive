package keepalive.urivalues;

import java.util.List;

import freenet.keys.FreenetURI;
import keepalive.exceptions.DAOException;

/**
 * Interface for a DAO that persist all values around {@link IUriValue}<br>
 * CRUD style methods for interaction with {@link IUriValue}
 */
public interface IUriValuesDAO {
	
	/**
	 * Saves a new {@link IUriValue} with a new unique {@link IUriValue#getUriId()}
	 * @param uri the freenet key
	 * @return the saved value (with the new unique uriId)
	 * @throws DAOException problems like already saved
	 */
	IUriValue create(FreenetURI uri) throws DAOException;
	
	/**
	 * Gets a {@link IUriValue} from the save
	 * @param uriId the unique uriId
	 * @return the saved value
	 * @throws DAOException if it doesnt exist
	 */
	IUriValue read(int uriId) throws DAOException;
	
	/**
	 * Updates a {@link IUriValue}
	 * @param uriValue the uriValue with all of its values
	 * @throws DAOException if it doesnt exist
	 */
	void update(IUriValue uriValue) throws DAOException;
	
	/**
	 * Deletes a {@link IUriValue} from the save
	 * @param uriId the unique uriId
	 * @throws DAOException if it doesnt exist
	 */
	void delete(int uriId) throws DAOException;
	
	/**
	 * Checks if a {@link IUriValue} with this freenetUri is already saved
	 * @param freenetUri the freenetUri
	 * @return if the freenetUri is already saved
	 * @throws DAOException
	 */
	boolean exist(FreenetURI freenetUri) throws DAOException;
	
	/**
	 * Returns a list with all saved {@link IUriValue}s
	 * @return
	 * @throws DAOException
	 */
	List<IUriValue> getAll() throws DAOException;
	
}
