package keepalive.service.reinserter;

public class FetchBlocksResult {
	
	private int successful = 0;
	private int failed = 0;
	
	public synchronized void addResult(boolean successful) {
		if (successful) {
			this.successful++;
		} else {
			failed++;
		}
	}
	
	public double calculatePersistenceRate() {
		return (double) successful / (successful + failed);
	}
	
}
