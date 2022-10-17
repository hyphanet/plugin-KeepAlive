package keepalive.exceptions;

public class DatabaseException extends Exception {
	
	private static final long serialVersionUID = 3156450661722048683L;
	
	public DatabaseException() {}
	
	public DatabaseException(String message) {
		super(message);
	}
	
	public DatabaseException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public DatabaseException(Throwable cause) {
		super(cause);
	}
	
	public DatabaseException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DatabaseException(String message, Throwable cause, Object... args) {
		super(String.format(message, args), cause);
	}
	
}
