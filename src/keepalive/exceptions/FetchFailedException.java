package keepalive.exceptions;

public class FetchFailedException extends Exception {
	
	private static final long serialVersionUID = -1746833000014867257L;
	
	public FetchFailedException() {}
	
	public FetchFailedException(String message) {
		super(message);
	}
	
	public FetchFailedException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public FetchFailedException(Throwable cause) {
		super(cause);
	}
	
	public FetchFailedException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public FetchFailedException(String message, Throwable cause, Object... args) {
		super(String.format(message, args), cause);
	}
	
}
