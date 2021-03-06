/*
 * ============LICENSE_START====================================================
 * org.onap.music.mdbc
 * =============================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
 * =============================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END======================================================
 */
package org.onap.music.mdbc.tests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provides the abstract interface for a Test, as well as some common functions.
 *
 * @author Robert Eby
 */
public abstract class Test {
	public static final String MDBC_DRIVER = "org.onap.music.mdbc.ProxyDriver";

	/**
	 * Each test derived from this class must implement this method,
	 * which runs the test and produces a list of error messages.
	 *
	 * @param config a JSONObject describing the configuration to use for this run of the test
	 * @return the list of messages.  If the list is empty, the test is considered to have run
	 * successfully.
	 */
	abstract public List<String> run(JSONObject config);

	public String getName() {
		String s = this.getClass().getName();
		return s.replaceAll("org.onap.music.mdbc.tests.", "");
	}

	public Properties buildProperties(JSONObject config, int i) {
		Properties p = new Properties();
		for (String key : config.keySet()) {
			if (key.equals("connections")) {
				JSONArray ja = config.getJSONArray("connections");
				JSONObject connection = ja.getJSONObject(i);
				for (String key2 : connection.keySet()) {
					p.setProperty(key2, connection.getString(key2));
				}
			} else {
				p.setProperty(key, config.getString(key));
			}
		}
		return p;
	}

	public Connection getDBConnection(Properties pr) throws SQLException, ClassNotFoundException {
		Class.forName(MDBC_DRIVER);
		String url = pr.getProperty("url");
		return DriverManager.getConnection(url, pr);
	}

	public void assertNotNull(Object o) throws Exception {
		if (o == null)
			throw new Exception("Object is null");
	}

	public void assertTableContains(int connid, Connection conn, String tbl, Object... kv) throws Exception {
		ResultSet rs = getRow(conn, tbl, kv);
		boolean throwit = !rs.next();
		rs.close();
		if (throwit) {
			throw new Exception("Conn id "+connid+" Table "+tbl+" does not have a row with "+catkeys(kv));
		}
	}
	public void assertTableDoesNotContain(int connid, Connection conn, String tbl, Object... kv) throws Exception {
		boolean throwit = true;
		try {
			assertTableContains(connid, conn, tbl, kv);
		} catch (Exception x) {
			throwit = false;
		}
		if (throwit) {
			throw new Exception("Conn id "+connid+" Table "+tbl+" does have a row with "+catkeys(kv));
		}
	}
	public ResultSet getRow(Connection conn, String tbl, Object... kv) throws SQLException {
		Statement stmt = conn.createStatement();
		StringBuilder sql = new StringBuilder("SELECT * FROM ")
			.append(tbl)
			.append(" WHERE ")
			.append(catkeys(kv));
		return stmt.executeQuery(sql.toString());
	}
	public String catkeys(Object... kv) {
		StringBuilder sql = new StringBuilder();
		String pfx = "";
		for (int i = 0; (i+1) < kv.length; i += 2) {
			sql.append(pfx).append(kv[i]).append("=");
			if (kv[i+1] instanceof String) {
				sql.append("'").append(kv[i+1]).append("'");
			} else {
				sql.append(kv[i+1].toString());
			}
			pfx = " AND ";
		}
		return sql.toString();
	}
}
