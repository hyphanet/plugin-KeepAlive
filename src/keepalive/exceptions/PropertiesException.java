package keepalive.exceptions;

public class PropertiesException extends Exception {
	
	private static final long serialVersionUID = -2845555980331219730L;
	
	public PropertiesException() {}
	
	public PropertiesException(String message) {
		super(message);
	}
	
	public PropertiesException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public PropertiesException(Throwable cause) {
		super(cause);
	}
	
	public PropertiesException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public PropertiesException(String message, Throwable cause, Object... args) {
		super(String.format(message, args), cause);
	}
	
}
