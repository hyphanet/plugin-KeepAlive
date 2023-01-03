/*
 * Keep Alive Plugin
 * Copyright (C) 2022 PlantEater
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package keepalive.model;

/**
 * enum for all property keys with default values<br>
 * they get automatically initialised
 */
public enum PropertiesKey {
	
	// common properties
	DB_VERSION("db_version"), //, "199"
	VERSION("version"), //, Plugin.VERSION
	LOGLEVEL("loglevel", 1),
	IDS("ids", ""),
	POWER("power", 6),
	ACTIVE("active", -1),
	SPLITFILE_TOLERANCE("splitfile_tolerance", 66),
	SPLITFILE_TEST_SIZE("splitfile_test_size", 18),
	LOG_LINKS("log_links", 1),
	LOG_UTC("log_utc", 1),
	SINGLE_URL_TIMESLOT("single_url_timeslot", 4),
	STACKTRACE("stackTrace", "false"),
	
	// uri specific
	URI("uri"),
	BLOCKS("blocks"),
	SUCCESS_SEGMENTS("success_segments"),
	SUCCESS("success"),
	HISTORY("history"),
	SEGMENT("segment");
	
	private final String key;
	private final Object defaultValue;
	
	PropertiesKey(String key, Object defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
	}
	
	PropertiesKey(String key) {
		this(key, null);
	}
	
	@Override
	public String toString() {
		return this.key;
	}
	
	public Object getDefault() {
		return this.defaultValue;
	}
	
}
