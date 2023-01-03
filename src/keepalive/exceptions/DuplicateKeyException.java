package keepalive.exceptions;

public class DuplicateKeyException extends Exception {
	
	private static final long serialVersionUID = 4112466609861664685L;
	
	public DuplicateKeyException() {}
	
	public DuplicateKeyException(String message) {
		super(message);
	}
	
	public DuplicateKeyException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public DuplicateKeyException(Throwable cause) {
		super(cause);
	}
	
	public DuplicateKeyException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DuplicateKeyException(String message, Throwable cause, Object... args) {
		super(String.format(message, args), cause);
	}
	
}
