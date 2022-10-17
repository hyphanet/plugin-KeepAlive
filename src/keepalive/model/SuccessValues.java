package keepalive.model;

import java.util.Objects;

public class SuccessValues {
	
	private final int success;
	private final int failed;
	private final int availableSegments;
	
	public SuccessValues(int success, int failed, int availableSegments) {
		this.success = success;
		this.failed = failed;
		this.availableSegments = availableSegments;
	}
	
	public int getSuccess() {
		return success;
	}
	
	public int getFailed() {
		return failed;
	}
	
	public int getAvailableSegments() {
		return availableSegments;
	}
	
	@Override
	public String toString() {
		return String.format("SuccessValues [success=%s, failed=%s, availableSegments=%s]", success, failed, availableSegments);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(availableSegments, failed, success);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		final SuccessValues other = (SuccessValues) obj;
		return availableSegments == other.availableSegments && failed == other.failed && success == other.success;
	}
	
}
