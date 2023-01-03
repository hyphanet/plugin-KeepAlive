package keepalive.model;

public enum UiKey {
	
	// Fields
	FORMPASSWORD("formPassword"),
	URIS("uris"),
	REMOVE_REGEX("remove_regex"),
	
	// Buttons
	MODIFY_LOGLEVEL("modify_loglevel"),
	CLEAR_LOGS("clear_logs"),
	CLEAR_HISTORY("clear_history"),
	REMOVE("remove"),
	REMOVE_WITH_REGEX("remove_with_regex"),
	REMOVE_ALL("remove_all"),
	MASTER_LOG("master_log"),
	LOG("log"),
	START("start"),
	STOP("stop"),
	
	// Unknown
	SHOW_LOG("show_log");
	
	private final String key;
	
	UiKey(String key) {
		this.key = key;
	}
	
	@Override
	public String toString() {
		return this.key;
	}
	
}
