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

public class Configuration {
    /** The property name to use to connect to cassandra*/
    public static final String KEY_CASSANDRA_URL = "cassandra.host";
    /** The property name to use to enable/disable the MusicSqlManager entirely. */
    public static final String KEY_DISABLED         = "disabled";
    /** The property name to use to select the DB 'mixin'. */
    public static final String KEY_DB_MIXIN_NAME    = "MDBC_DB_MIXIN";
    /** The property name to use to select the MUSIC 'mixin'. */
    public static final String KEY_MUSIC_MIXIN_NAME = "MDBC_MUSIC_MIXIN";
    /** The property name to select if async staging table update is used */
    public static final String KEY_ASYNC_STAGING_TABLE_UPDATE = "ASYNC_STAGING_TABLE_UPDATE";
    /** The name of the default mixin to use for the DBInterface. */
    public static final String DB_MIXIN_DEFAULT     = "mysql";//"h2";
    /** The name of the default mixin to use for the MusicInterface. */
    public static final String MUSIC_MIXIN_DEFAULT  = "cassandra2";//"cassandra2";
    /** Default cassandra ulr*/
    public static final String CASSANDRA_URL_DEFAULT = "localhost";//"cassandra2";
    /** Name of Tx Digest Update Daemon sleep time */
	public static final String TX_DAEMON_SLEEPTIME_S = "txdaemonsleeps";
	/** Default txDigest Daemon sleep time */
	public static final String TX_DAEMON_SLEEPTIME_S_DEFAULT = "10";
    /**  The property name to use to provide a timeout to mdbc (ownership) */
    public static final String KEY_OWNERSHIP_TIMEOUT = "mdbc_timeout";
    /** The default property value to use for the MDBC timeout */
    public static final long DEFAULT_OWNERSHIP_TIMEOUT = 5*60*60*1000;//default of 5 hours
    /** The property name to provide comma separated list of ranges to warmup */
    public static final String KEY_WARMUPRANGES = "warmupranges";
	/** Default async staging table update o ption*/
	public static final String ASYNC_STAGING_TABLE_UPDATE = "false";
	/** The property name to determine if only write locks are allowed */
	public static final String KEY_WRITE_LOCKS_ONLY = "write_locks_only";
	/** Default if only write locks are allowed */
	public static final Boolean WRITE_LOCK_ONLY_DEFAULT = false;
}
