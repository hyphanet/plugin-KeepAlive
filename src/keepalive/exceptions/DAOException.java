package keepalive.exceptions;

public class DAOException extends Exception {
	
	private static final long serialVersionUID = 87724461226632647L;
	
	public DAOException() {}
	
	public DAOException(String message) {
		super(message);
	}
	
	public DAOException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public DAOException(Throwable cause) {
		super(cause);
	}
	
	public DAOException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DAOException(String message, Throwable cause, Object... args) {
		super(String.format(message, args), cause);
	}
	
}
