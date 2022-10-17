/*
 * Keep Alive Plugin
 * Copyright (C) 2012 Jeriadoc
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
package keepalive.repository.impl;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import org.h2.jdbc.JdbcSQLNonTransientException;
import org.h2.tools.Upgrade;

import keepalive.Plugin;
import keepalive.exceptions.DatabaseException;
import keepalive.model.PropertiesKey;
import keepalive.repository.IDatabaseBlock;
import keepalive.repository.IDatabaseDAO;

/**
 * The H2 database settings
 */
public class H2DatabaseDAO implements IDatabaseDAO {
	
	private final Plugin plugin;
	
	private static final String JDBC_DRIVER = "org.h2.Driver";
	private static final String DB_URL = String.format("jdbc:h2:%s%sKeepAlive%2$skeppalive", System.getProperty("user.dir"), File.separator);
	private static final String USER = "sa";
	private static final String PASS = "";
	private static int updateTries = 0;
	private static Class<?> jdbcDriverClass = null;
	
	private static final String SQL_SAVE = "INSERT INTO Block (uri, data) VALUES (?, ?)";
	private static final String SQL_FIND = "SELECT data FROM Block WHERE uri = ?";
	private static final String SQL_UPDATE = "UPDATE Block SET data = ? WHERE uri = ?";
	private static final String SQL_DELETE = "DELETE FROM Block WHERE uri = ?;";
	private static final String SQL_LAST_ACCESS_DIFF = "SELECT TIMESTAMPDIFF(MILLISECOND, last_access, CURRENT_TIMESTAMP) FROM Block WHERE uri = ?";
	private static final String SQL_LAST_ACCESS_UPDATE = "UPDATE Block SET last_access = CURRENT_TIMESTAMP WHERE uri = ?";
	
	public H2DatabaseDAO(Plugin plugin) {
		this.plugin = plugin;
	}
	
	public synchronized Connection getConnection() throws DatabaseException {
		try {
			if (jdbcDriverClass == null)
				jdbcDriverClass = Class.forName(JDBC_DRIVER);
			
			return DriverManager.getConnection(DB_URL, USER, PASS);
		} catch (final JdbcSQLNonTransientException e) {
			// try one time to upgrade the database
			if (updateTries++ <= 0 && e.getMessage().contains("The write format 1 is smaller than the supported format 2")) {
				upgradeDB();
				return getConnection();
			}
			
			throw new DatabaseException(e);
		} catch (final Exception e) {
			throw new DatabaseException(e);
		}
	}
	
	@Override
	public void pluginStart() {
		try (Connection connection = getConnection();
				Statement statement = connection.createStatement()) {
			final String sql = "CREATE TABLE IF NOT EXISTS Block (" +
					"uri VARCHAR(256) PRIMARY KEY, " +
					"data VARBINARY(32768) not null, " +
					"last_access TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
			statement.executeUpdate(sql);
		} catch (final Exception e) {
			plugin.log(e.getMessage(), e);
		}
	}
	
	private void upgradeDB() throws DatabaseException {
		try {
			final int fromDBVersion = plugin.getIntProp(PropertiesKey.DB_VERSION);
			plugin.log("upgradeDB from version -> " + fromDBVersion);
			
			final Properties props = new Properties();
			props.put("user", USER);
			props.put("password", PASS);
			final boolean updateBool = Upgrade.upgrade(DB_URL, props, fromDBVersion);
			if (updateBool) {
				plugin.setProp(PropertiesKey.DB_VERSION, "206");
				plugin.saveProp();
				plugin.log("upgradeDB successful to version -> " + plugin.getIntProp(PropertiesKey.DB_VERSION));
			} else {
				plugin.log("upgradeDB was NOT successful");
			}
		} catch (final Exception e) {
			throw new DatabaseException(e);
		}
	}
	
	@Override
	public IDatabaseBlock create(String uri, byte[] data) {
		if (exist(uri) || data == null)
			return null;
		
		IDatabaseBlock result = null;
		
		try (Connection connection = getConnection();
				PreparedStatement savePreparedStatement = connection.prepareStatement(SQL_SAVE)) {
			savePreparedStatement.setString(1, uri);
			savePreparedStatement.setBytes(2, data);
			savePreparedStatement.executeUpdate();
			
			result = new DatabaseBlock(uri, data);
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + uri, e);
		}
		
		return result;
	}
	
	@Override
	public IDatabaseBlock read(String uri) {
		IDatabaseBlock result = null;
		
		try (Connection connection = getConnection();
				PreparedStatement preparedStatement = connection.prepareStatement(SQL_FIND)) {
			preparedStatement.setString(1, uri);
			
			final ResultSet resultSet = preparedStatement.executeQuery();
			if (resultSet.next()) {
				final byte[] data = resultSet.getBytes("data");
				
				if (resultSet.next()) {
					plugin.log("Not unique uri: " + uri);
					return null;
				}
				
				result = new DatabaseBlock(uri, data);
			}
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + uri, e);
		}
		
		return result;
	}
	
	@Override
	public void update(IDatabaseBlock databaseBlock) {
		if (databaseBlock == null || !exist(databaseBlock.getUri()))
			return;
		
		try (Connection connection = getConnection();
				PreparedStatement updatePreparedStatement = connection.prepareStatement(SQL_UPDATE)) {
			updatePreparedStatement.setBytes(1, databaseBlock.getData());
			updatePreparedStatement.setString(2, databaseBlock.getUri());
			updatePreparedStatement.executeUpdate();
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + databaseBlock.getUri(), e);
		}
	}
	
	@Override
	public void delete(String uri) {
		if (!exist(uri))
			return;
		
		try (Connection connection = getConnection();
				PreparedStatement preparedStatement = connection.prepareStatement(SQL_DELETE)) {
			preparedStatement.setString(1, uri);
			preparedStatement.executeUpdate();
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + uri, e);
		}
	}
	
	@Override
	public boolean exist(String uri) {
		if (uri == null)
			return false;
		
		return read(uri) != null;
	}
	
	@Override
	public long lastAccessDiff(String uri) {
		try (Connection connection = getConnection();
				PreparedStatement preparedStatement = connection.prepareStatement(SQL_LAST_ACCESS_DIFF)) {
			preparedStatement.setString(1, uri);
			final ResultSet resultSet = preparedStatement.executeQuery();
			if (!resultSet.next()) {
				return 0;
			}
			final long diff = resultSet.getLong(1);
			
			if (resultSet.next()) {
				plugin.log("Not unique uri: " + uri);
				return 0;
			}
			
			return diff;
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + uri, e);
		}
		
		return 0;
	}
	
	@Override
	public void lastAccessUpdate(String uri) {
		try (Connection connection = getConnection();
				PreparedStatement preparedStatement = connection.prepareStatement(SQL_LAST_ACCESS_UPDATE)) {
			preparedStatement.setString(1, uri);
			preparedStatement.executeUpdate();
		} catch (final Exception e) {
			plugin.log(e.getMessage() + " " + uri, e);
		}
	}
	
}
