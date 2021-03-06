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
package org.onap.music.mdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import org.apache.commons.lang3.StringUtils;
import org.onap.music.exceptions.MDBCServiceException;
import org.onap.music.logging.EELFLoggerDelegate;

/**
 * ProxyStatement is a proxy Statement that front ends Statements from the underlying JDBC driver.  It passes all operations through,
 * and invokes the MusicSqlManager when there is the possibility that database tables have been created or dropped.
 *
 * @author Robert Eby
 */
public class MdbcPreparedStatement extends MdbcStatement implements PreparedStatement {
	private EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(MdbcPreparedStatement.class);
	private static final String DATASTAX_PREFIX = "com.datastax.driver";

	final String sql;			// holds the sql statement if prepared statement
	String[] params;			// holds the parameters if prepared statement, indexing starts at 1


	public MdbcPreparedStatement(Statement stmt, MdbcConnection mConn) {
		super(stmt, mConn);
		this.sql = null;
	}

	public MdbcPreparedStatement(Statement stmt, String sql, MdbcConnection mConn) {
		super(stmt, sql, mConn);
		this.sql = sql;
		//indexing starts at 1
		params = new String[StringUtils.countMatches(sql, "?")+1];
	}
	
	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return stmt.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return stmt.isWrapperFor(iface);
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeQuery: "+sql);
		ResultSet r = null;
		try {
			mConn.preStatementHook(sql);
			r = stmt.executeQuery(sql);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger, "executeQuery: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
		} catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return r;
	}

	@Override
	public int executeUpdate(String sql) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeUpdate: "+sql);
		
		int n = 0;
		try {
			mConn.preStatementHook(sql);
			n = stmt.executeUpdate(sql);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger, "executeUpdate: exception "+nm+" "+e);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return n;
	}

	@Override
	public void close() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"Statement close: ");
		stmt.close();
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"getMaxFieldSize");
		return stmt.getMaxFieldSize();
	}

	@Override
	public void setMaxFieldSize(int max) throws SQLException {
		stmt.setMaxFieldSize(max);
	}

	@Override
	public int getMaxRows() throws SQLException {
		return stmt.getMaxRows();
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		stmt.setMaxRows(max);
	}

	@Override
	public void setEscapeProcessing(boolean enable) throws SQLException {
		stmt.setEscapeProcessing(enable);
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		return stmt.getQueryTimeout();
	}

	@Override
	public void setQueryTimeout(int seconds) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"setQueryTimeout seconds "+ seconds);
		stmt.setQueryTimeout(seconds);
	}

	@Override
	public void cancel() throws SQLException {
		stmt.cancel();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return stmt.getWarnings();
	}

	@Override
	public void clearWarnings() throws SQLException {
		stmt.clearWarnings();
	}

	@Override
	public void setCursorName(String name) throws SQLException {
		stmt.setCursorName(name);
	}

	@Override
	public boolean execute(String sql) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"execute: "+sql);
		boolean b = false;
		try {
			mConn.preStatementHook(sql);
			b = stmt.execute(sql);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger, "execute: exception "+nm+" "+e);
			// Note: this seems to be the only call Camunda uses, so it is the only one I am fixing for now.
			boolean ignore = nm.startsWith(DATASTAX_PREFIX);
//			ignore |= (nm.startsWith("org.h2.jdbc.JdbcSQLException") && e.getMessage().contains("already exists"));
			if (ignore) {
				logger.warn("execute: exception (IGNORED) "+nm);
			} else {
				logger.error(EELFLoggerDelegate.errorLogger, " Exception "+nm+" "+e);
				throw e;
			}
		} catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return b;
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return stmt.getResultSet();
	}

	@Override
	public int getUpdateCount() throws SQLException {
		return stmt.getUpdateCount();
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		return stmt.getMoreResults();
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		stmt.setFetchDirection(direction);
	}

	@Override
	public int getFetchDirection() throws SQLException {
		return stmt.getFetchDirection();
	}

	@Override
	public void setFetchSize(int rows) throws SQLException {
		stmt.setFetchSize(rows);
	}

	@Override
	public int getFetchSize() throws SQLException {
		return stmt.getFetchSize();
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		return stmt.getResultSetConcurrency();
	}

	@Override
	public int getResultSetType() throws SQLException {
		return stmt.getResultSetType();
	}

	@Override
	public void addBatch(String sql) throws SQLException {
		stmt.addBatch(sql);
	}

	@Override
	public void clearBatch() throws SQLException {
		stmt.clearBatch();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeBatch: ");
		int[] n = null;
		try {
			logger.debug(EELFLoggerDelegate.applicationLogger,"executeBatch() is not supported by MDBC; your results may be incorrect as a result.");
			n = stmt.executeBatch();
		} catch (Exception e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeBatch: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
		}
		return n;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return stmt.getConnection();
	}

	@Override
	public boolean getMoreResults(int current) throws SQLException {
		return stmt.getMoreResults(current);
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return stmt.getGeneratedKeys();
	}

	@Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeUpdate: "+sql);
		int n = 0;
		try {
			mConn.preStatementHook(sql);
			n = stmt.executeUpdate(sql, autoGeneratedKeys);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeUpdate: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
		} catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return n;
	}

	@Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeUpdate: "+sql);
		int n = 0;
		try {
			mConn.preStatementHook(sql);
			n = stmt.executeUpdate(sql, columnIndexes);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeUpdate: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
        }
		return n;
	}

	@Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeUpdate: "+sql);
		int n = 0;
		try {
			mConn.preStatementHook(sql);
			n = stmt.executeUpdate(sql, columnNames);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeUpdate: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return n;
	}

	@Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"execute: "+sql);
		boolean b = false;
		try {
			mConn.preStatementHook(sql);
			b = stmt.execute(sql, autoGeneratedKeys);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"execute: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return b;
	}

	@Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"execute: "+sql);
		boolean b = false;
		try {
			mConn.preStatementHook(sql);
			b = stmt.execute(sql, columnIndexes);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"execute: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
		} catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return b;
	}

	@Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"execute: "+sql);
		boolean b = false;
		try {
			mConn.preStatementHook(sql);
			b = stmt.execute(sql, columnNames);
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"execute: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return b;
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		return stmt.getResultSetHoldability();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return stmt.isClosed();
	}

	@Override
	public void setPoolable(boolean poolable) throws SQLException {
		stmt.setPoolable(poolable);
	}

	@Override
	public boolean isPoolable() throws SQLException {
		return stmt.isPoolable();
	}

	@Override
	public void closeOnCompletion() throws SQLException {
		stmt.closeOnCompletion();
	}

	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		return stmt.isCloseOnCompletion();
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeQuery: "+sql);
		ResultSet r = null;
		try {
			mConn.preStatementHook(sql);
			r = ((PreparedStatement)stmt).executeQuery();;
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			e.printStackTrace();
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeQuery: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}


		return r;
	}

	@Override
	public int executeUpdate() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"executeUpdate: "+sql);
		int n = 0;
		try {
			mConn.preStatementHook(sql);
			n = ((PreparedStatement)stmt).executeUpdate();
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			e.printStackTrace();
			String nm = e.getClass().getName();
			logger.error(EELFLoggerDelegate.errorLogger,"executeUpdate: exception "+nm);
			if (!nm.startsWith(DATASTAX_PREFIX))
				throw e;
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return n;
	}

	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		((PreparedStatement)stmt).setNull(parameterIndex, sqlType);
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		((PreparedStatement)stmt).setBoolean(parameterIndex, x);
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		((PreparedStatement)stmt).setByte(parameterIndex, x);
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		((PreparedStatement)stmt).setShort(parameterIndex, x);
	}

	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		((PreparedStatement)stmt).setInt(parameterIndex, x);
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		((PreparedStatement)stmt).setLong(parameterIndex, x);
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		((PreparedStatement)stmt).setFloat(parameterIndex, x);
	}

	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		((PreparedStatement)stmt).setDouble(parameterIndex, x);
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		((PreparedStatement)stmt).setBigDecimal(parameterIndex, x);
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		((PreparedStatement)stmt).setString(parameterIndex, x);
		params[parameterIndex] = x;
	}

	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		((PreparedStatement)stmt).setBytes(parameterIndex, x);
	}

	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		((PreparedStatement)stmt).setDate(parameterIndex, x);
	}

	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		((PreparedStatement)stmt).setTime(parameterIndex, x);
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		((PreparedStatement)stmt).setTimestamp(parameterIndex, x);
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
		((PreparedStatement)stmt).setAsciiStream(parameterIndex, x, length);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
		((PreparedStatement)stmt).setUnicodeStream(parameterIndex, x, length);
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
		((PreparedStatement)stmt).setBinaryStream(parameterIndex, x, length);
	}

	@Override
	public void clearParameters() throws SQLException {
		((PreparedStatement)stmt).clearParameters();
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		((PreparedStatement)stmt).setObject(parameterIndex, x, targetSqlType);
	}

	@Override
	public void setObject(int parameterIndex, Object x) throws SQLException {
		((PreparedStatement)stmt).setObject(parameterIndex, x);
	}

	@Override
	public boolean execute() throws SQLException {
		logger.debug(EELFLoggerDelegate.applicationLogger,"execute: "+sql);
		boolean b = false;
		try {
			mConn.preStatementHook(sql);
			b = ((PreparedStatement)stmt).execute();
			mConn.postStatementHook(sql);
		} catch (SQLException e) {
			e.printStackTrace();
			String nm = e.getClass().getName();
			// Note: this seems to be the only call Camunda uses, so it is the only one I am fixing for now.
			boolean ignore = nm.startsWith(DATASTAX_PREFIX);
//			ignore |= (nm.startsWith("org.h2.jdbc.JdbcSQLException") && e.getMessage().contains("already exists"));
			if (ignore) {
				logger.warn("execute: exception (IGNORED) "+nm);
			} else {
				logger.error(EELFLoggerDelegate.errorLogger,"execute: exception "+nm);
				throw e;
			}
        } catch (MDBCServiceException e) {
		    throw new SQLException(e.getMessage(), e);
		}
		return b;
	}

	@Override
	public void addBatch() throws SQLException {
		((PreparedStatement)stmt).addBatch();
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
		((PreparedStatement)stmt).setCharacterStream(parameterIndex, reader, length);
	}

	@Override
	public void setRef(int parameterIndex, Ref x) throws SQLException {
		((PreparedStatement)stmt).setRef(parameterIndex, x);
	}

	@Override
	public void setBlob(int parameterIndex, Blob x) throws SQLException {
		((PreparedStatement)stmt).setBlob(parameterIndex, x);
	}

	@Override
	public void setClob(int parameterIndex, Clob x) throws SQLException {
		((PreparedStatement)stmt).setClob(parameterIndex, x);
	}

	@Override
	public void setArray(int parameterIndex, Array x) throws SQLException {
		((PreparedStatement)stmt).setArray(parameterIndex, x);
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return ((PreparedStatement)stmt).getMetaData();
	}

	@Override
	public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
		((PreparedStatement)stmt).setDate(parameterIndex, x, cal);
	}

	@Override
	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
		((PreparedStatement)stmt).setTime(parameterIndex, x, cal);
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
		((PreparedStatement)stmt).setTimestamp(parameterIndex, x, cal);
	}

	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		((PreparedStatement)stmt).setNull(parameterIndex, sqlType, typeName);
	}

	@Override
	public void setURL(int parameterIndex, URL x) throws SQLException {
		((PreparedStatement)stmt).setURL(parameterIndex, x);
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		return ((PreparedStatement)stmt).getParameterMetaData();
	}

	@Override
	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		((PreparedStatement)stmt).setRowId(parameterIndex, x);
	}

	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		((PreparedStatement)stmt).setNString(parameterIndex, value);
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
		((PreparedStatement)stmt).setNCharacterStream(parameterIndex, value, length);
	}

	@Override
	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		((PreparedStatement)stmt).setNClob(parameterIndex, value);
	}

	@Override
	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
		((PreparedStatement)stmt).setClob(parameterIndex, reader, length);
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		((PreparedStatement)stmt).setBlob(parameterIndex, inputStream, length);
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
		((PreparedStatement)stmt).setNClob(parameterIndex, reader, length);
	}

	@Override
	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		((PreparedStatement)stmt).setSQLXML(parameterIndex, xmlObject);
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		((PreparedStatement)stmt).setObject(parameterIndex, x, targetSqlType, scaleOrLength);
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
		((PreparedStatement)stmt).setAsciiStream(parameterIndex, x, length);
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
		((PreparedStatement)stmt).setBinaryStream(parameterIndex, x, length);
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
		((PreparedStatement)stmt).setCharacterStream(parameterIndex, reader, length);
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
		((PreparedStatement)stmt).setAsciiStream(parameterIndex, x);
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
		((PreparedStatement)stmt).setBinaryStream(parameterIndex, x);
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		((PreparedStatement)stmt).setCharacterStream(parameterIndex, reader);
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
		((PreparedStatement)stmt).setNCharacterStream(parameterIndex, value);
	}

	@Override
	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		((PreparedStatement)stmt).setClob(parameterIndex, reader);
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
		((PreparedStatement)stmt).setBlob(parameterIndex, inputStream);
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		((PreparedStatement)stmt).setNClob(parameterIndex, reader);
	}
	
}
