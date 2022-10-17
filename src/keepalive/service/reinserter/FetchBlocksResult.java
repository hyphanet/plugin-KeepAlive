package keepalive.service.reinserter;

class FetchBlocksResult {
	
	private int successful = 0;
	private int failed = 0;
	
	void addResult(boolean successful) {
		if (successful) {
			this.successful++;
		} else {
			failed++;
		}
	}
	
	double calculatePersistenceRate() {
		return (double) successful / (successful + failed);
	}
	
}
