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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.onap.music.exceptions.MDBCServiceException;
import org.onap.music.exceptions.QueryException;
import org.onap.music.logging.EELFLoggerDelegate;
import org.onap.music.logging.format.AppMessages;
import org.onap.music.logging.format.ErrorSeverity;
import org.onap.music.logging.format.ErrorTypes;
import org.onap.music.mdbc.mixins.DBInterface;
import org.onap.music.mdbc.mixins.MixinFactory;
import org.onap.music.mdbc.mixins.MusicInterface;
import org.onap.music.mdbc.mixins.MusicInterface.OwnershipReturn;
import org.onap.music.mdbc.ownership.OwnershipAndCheckpoint;
import org.onap.music.mdbc.tables.MriReference;
import org.onap.music.mdbc.tables.MusicTxDigestDaemon;
import org.onap.music.mdbc.tables.MusicTxDigestId;
import org.onap.music.mdbc.tables.TxCommitProgress;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * \TODO Implement an interface for the server logic and a factory 
 * @author Enrique Saurez
 */
public class StateManager {

	//\TODO We need to fix the auto-commit mode and multiple transactions with the same connection

	private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(StateManager.class);

	/**
	 * This is the interface used by all the MusicSqlManagers, 
	 * that are created by the MDBC Server 
	 * @see MusicInterface 
     */
    private MusicInterface musicInterface;
    /**
     * This is the Running Queries information table.
     * It mainly contains information about the entities 
     * that have being committed so far.
     */
    private TxCommitProgress transactionInfo;
    private Map<String,MdbcConnection> mdbcConnections;
    private String sqlDBName;
    private String sqlDBUrl;
    
    String musicmixin;
    String cassandraUrl;
    private Properties info;
    
    /** Identifier for this server instance */
    private String mdbcServerName;
    private Map<String,DatabasePartition> connectionRanges;//Each connection owns its own database partition
    private final Lock eventualLock  = new ReentrantLock();
    private Set<Range> eventualRanges;
    /** lock for warmupRanges */
    private final Lock warmupLock = new ReentrantLock();
    /** a set of ranges that should be periodically updated with latest information, if null all tables should be warmed up */
    private Set<Range> rangesToWarmup;
    /** map of transactions that have already been applied/updated in this sites SQL db */
    private Map<Range, Pair<MriReference, MusicTxDigestId>> alreadyApplied;
    private OwnershipAndCheckpoint ownAndCheck;
    private Thread txDaemon ;

    /**
     * For testing purposes only
     */
    @Deprecated
    public StateManager() {
    }
    
	public StateManager(String sqlDBUrl, Properties newInfo, String mdbcServerName, String sqlDBName) throws MDBCServiceException {
        this.sqlDBName = sqlDBName;
        this.sqlDBUrl = cleanSqlUrl(sqlDBUrl);
        this.info = new Properties();
        this.mdbcServerName = mdbcServerName;
    
        this.connectionRanges = new ConcurrentHashMap<>();
        this.transactionInfo = new TxCommitProgress();
        //\fixme this might not be used, delete?
        try {
			info.load(this.getClass().getClassLoader().getResourceAsStream("music.properties"));
			info.load(this.getClass().getClassLoader().getResourceAsStream("key.properties"));
			info.putAll(MDBCUtils.getMdbcProperties());
		} catch (IOException e) {
			logger.error(EELFLoggerDelegate.errorLogger, e.getMessage());
		}
		info.putAll(newInfo);
        cassandraUrl = info.getProperty(Configuration.KEY_CASSANDRA_URL, Configuration.CASSANDRA_URL_DEFAULT);
        musicmixin = info.getProperty(Configuration.KEY_MUSIC_MIXIN_NAME, Configuration.MUSIC_MIXIN_DEFAULT);
        
        String writeLocksOnly = info.getProperty(Configuration.KEY_WRITE_LOCKS_ONLY);
        MDBCUtils.writeLocksOnly = (writeLocksOnly==null) ? Configuration.WRITE_LOCK_ONLY_DEFAULT : Boolean.parseBoolean(writeLocksOnly);
        
        initMusic();
        Map<Range, Pair<MriReference, MusicTxDigestId>> alreadyApplied = initSqlDatabase();

        String t = info.getProperty(Configuration.KEY_OWNERSHIP_TIMEOUT);
        long timeout = (t == null) ? Configuration.DEFAULT_OWNERSHIP_TIMEOUT : Integer.parseInt(t);
        ownAndCheck = new OwnershipAndCheckpoint(alreadyApplied, timeout);
        
        initTxDaemonThread();
    }

    protected String cleanSqlUrl(String url){
	    if(url!=null) {
            url = url.trim();
            if (url.length() > 0 && url.charAt(url.length() - 1) == '/') {
                url= url.substring(0, url.length() - 1);
            }
        }
        return url;
    }

    protected void initTxDaemonThread(){
        txDaemon = new Thread(
            new MusicTxDigestDaemon(Integer.parseInt(
                info.getProperty(Configuration.TX_DAEMON_SLEEPTIME_S, Configuration.TX_DAEMON_SLEEPTIME_S_DEFAULT)),
                this));
        txDaemon.setName("TxDaemon");
        txDaemon.setDaemon(true);
        txDaemon.start();
    }

    /**
     * Initialize all the  interfaces and datastructures
     * @throws MDBCServiceException
     */
    protected void initMusic() throws MDBCServiceException {
        this.musicInterface = MixinFactory.createMusicInterface(this, musicmixin, mdbcServerName, info);
        this.mdbcConnections = new HashMap<>();
    }
    
    /**
     * Do everything necessary to initialize the sql database
     * @return the current checkpoint location of this database, if restarting
     * @throws MDBCServiceException
     */
    protected Map<Range, Pair<MriReference, MusicTxDigestId>> initSqlDatabase() throws MDBCServiceException {
        if(!this.sqlDBUrl.toLowerCase().startsWith("jdbc:postgresql")) {
            try {
                Connection sqlConnection = DriverManager.getConnection(this.sqlDBUrl, this.info);
                StringBuilder sql = new StringBuilder("CREATE DATABASE IF NOT EXISTS ")
                    .append(sqlDBName)
                    .append(";");
                Statement stmt = sqlConnection.createStatement();
                stmt.execute(sql.toString());
                sqlConnection.close();
            } catch (SQLException e) {
                logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(), AppMessages.UNKNOWNERROR,
                    ErrorSeverity.CRITICAL,
                    ErrorTypes.GENERALSERVICEERROR);
                throw new MDBCServiceException(e.getMessage(), e);
            }
        }
        
        Map<Range, Pair<MriReference, MusicTxDigestId>> alreadyAppliedToDb = null;
        try {
            MdbcConnection mdbcConn = (MdbcConnection) openConnection("init");
            mdbcConn.initDatabase();
            alreadyAppliedToDb = mdbcConn.getDBInterface().getCheckpointLocations();
            closeConnection("init");
        } catch (QueryException e) {
            logger.error("Error initializing sql database tables");
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(), AppMessages.QUERYERROR, ErrorTypes.QUERYERROR, ErrorSeverity.CRITICAL);
        }

        if (alreadyAppliedToDb==null) {
            alreadyAppliedToDb = new ConcurrentHashMap<>();
        }
        return alreadyAppliedToDb;
    }
    
    /**
     * Get list of ranges to warmup from configuration file
     * if no configuration is provided, will return null
     * @return
     */
    private Set<Range> initWarmupRanges() {
        String warmupString = info.getProperty(Configuration.KEY_WARMUPRANGES);
        if (warmupString==null) {
            return null;
        }
        Set<Range> warmupRanges = new HashSet<>();
        String[] ranges = warmupString.split(",");
        for (String range: ranges) {
            warmupRanges.add(new Range(range.trim()));
        }
        return warmupRanges;
    }

    public MusicInterface getMusicInterface() {
    	return this.musicInterface;
    }
    
    public List<DatabasePartition> getPartitions() {
        return new ArrayList<>(connectionRanges.values());
	}

    /**
     * Get a list of ranges that are to be periodically warmed up
     * 
     * If no list is specified, all ranges except eventual consistency ranges are returned
     * @return
     */
	public Set<Range> getRangesToWarmup() {
        warmupLock.lock();
        Set<Range> returnSet;
        try {
            if(rangesToWarmup!=null) {
                returnSet = rangesToWarmup;
            }
            else {
                returnSet = getAllRanges();
                for (Range eventualRange: eventualRanges) {
                    returnSet.remove(eventualRange);
                }
            }
        }
        finally{
           warmupLock.unlock();
        }
        return returnSet;
    }

	/**
	 * Get a set of all ranges seen in the sql db
	 * @return
	 */
	private Set<Range> getAllRanges() {
	    DBInterface dbi = ((MdbcConnection) getConnection("daemon")).getDBInterface();
	    return dbi.getSQLRangeSet();
    }

    /**
	 * Get a list of ranges that are eventually consistent
	 * @return
	 */
    public Set<Range> getEventualRanges() {
        eventualLock.lock();
        Set<Range> returnSet;
        try {
            if(eventualRanges!=null){
                returnSet = new HashSet<>(eventualRanges);
            }
            else{
                returnSet= new HashSet<>();
            }
        }
        finally{
            eventualLock.unlock();
        }
        return returnSet;
    }

    public void setEventualRanges(Set<Range> eventualRanges) {
        eventualLock.lock();
        try {
            this.eventualRanges = eventualRanges;
        }
        finally{
            eventualLock.unlock();
        }
    }
    
    public String getMdbcServerName() {
        return mdbcServerName;
    }

    public void setMdbcServerName(String mdbcServerName) {
        this.mdbcServerName = mdbcServerName;
    }

    /**
     * Close connection and relinquish any locks held for that connection
     * @param connectionId
     */
    public void closeConnection(String connectionId){
        //\TODO check if there is a race condition
        if(mdbcConnections.containsKey(connectionId)) {
            transactionInfo.deleteTxProgress(connectionId);
            try {
                Connection conn = mdbcConnections.get(connectionId);
                if (conn!=null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.UNKNOWNERROR, ErrorSeverity.CRITICAL,
                        ErrorTypes.GENERALSERVICEERROR);
            }
            mdbcConnections.remove(connectionId);
        }
        
        connectionRanges.remove(connectionId);
        
    }

    /**
     * Opens a connection into database, setting up all necessary triggers, etc
     * @param id UUID of a connection
     */
	public Connection openConnection(String id) {
		Connection sqlConnection;
    	MdbcConnection newConnection;
        Utils.registerDefaultDrivers();
        //Create connection to local SQL DB

		try {
			sqlConnection = DriverManager.getConnection(this.sqlDBUrl+"/"+this.sqlDBName, this.info);
		} catch (SQLException e) {
		    logger.error("sql connection was not created correctly");
			logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.QUERYERROR, ErrorSeverity.CRITICAL,
                    ErrorTypes.QUERYERROR);
			sqlConnection = null;
		}
		
		//TODO: later we could try to match it to some more sticky client id
        DatabasePartition ranges=new DatabasePartition(musicInterface.generateUniqueKey());
        connectionRanges.put(id,ranges);
        
        //Create MDBC connection
    	try {
			newConnection = new MdbcConnection(id,this.sqlDBUrl+"/"+this.sqlDBName, sqlConnection, info, this.musicInterface,
                transactionInfo,ranges, this);
		} catch (MDBCServiceException e) {
			logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(),AppMessages.UNKNOWNERROR, ErrorSeverity.CRITICAL,
                    ErrorTypes.QUERYERROR);
			newConnection = null;
		}
		logger.info(EELFLoggerDelegate.applicationLogger,"Connection created for connection: "+id);

    	transactionInfo.createNewTransactionTracker(id, sqlConnection);
    	if(newConnection != null) {
            mdbcConnections.put(id,newConnection);
        }
    	return newConnection;
	}
    
    
    /**
     * This function returns the connection to the corresponding transaction 
     * @param id of the transaction, created using
     * @return
     */
    public Connection getConnection(String id) {
        if(mdbcConnections.containsKey(id)) {
            //\TODO: Verify if this make sense
            // Intent: reinitialize transaction progress, when it already completed the previous tx for the same connection
            if(transactionInfo.isComplete(id)) {
                transactionInfo.reinitializeTxProgress(id);
            }
            return mdbcConnections.get(id);
        }

        return openConnection(id);
    }
    
	public void initializeSystem() {
		//\TODO Prefetch data to system using the data ranges as guide 
		throw new UnsupportedOperationException("Function initialize system needs to be implemented id MdbcStateManager");
	}

	private void relinquish(DatabasePartition partition){
        try {
            musicInterface.relinquish(partition);
        } catch (MDBCServiceException e) {
            logger.error("Relinquish failed, would need to forcefully obtain lock later");
        }

    }

    public void setWarmupRanges(Set<Range> warmupRanges) {
        warmupLock.lock();
        try {
            this.rangesToWarmup = warmupRanges;
        }
        finally{
            warmupLock.unlock();
        }
    }
    
    public OwnershipAndCheckpoint getOwnAndCheck() {
        return ownAndCheck;
    }

    /**
     * Close all connections for this server, relinquishing any locks/partitions owned by this server
     */
    public void releaseAllPartitions() {
        for(String connection: this.mdbcConnections.keySet()) {
            closeConnection(connection);
        } 
    }
}
