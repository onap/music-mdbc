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

/**
 * <p>
 * This package provides a testing harness to test the various features of MDBC against
 * multiple combinations of database and MUSIC mixins.  The configurations (consisting of
 * database information and mixin combinations) to test, as well as the specific tests to
 * run are all defined in the configuration file <code>test.json</code>.
 * </p>
 * <p>
 * To run the tests against all the configurations specified in /tests.json, do the following:
 * </p>
 * <pre>
 * 	java org.onap.music.mdbc.tests.MAIN [ configfile ]
 * </pre>
 * <p>
 * It is assumed that a copy of Cassandra is running locally on port 9042,
 * that a copy of H2 server is is running locally on port 8082,
 * and that a copy of MySQL (or MariaDB) is running locally on port 3306.
 * These can be adjusted by editing the /tests.json file.
 * </p>
 * <p>
 * When building a copy of MDBC for production use, this package can be safely removed.
 * </p>
 * <p>
 * The initial copy of <i>tests.json</i> is as follows:
 * </p>
 * <pre>
 * {
 *	"tests": [
 *		"org.onap.music.mdbc.tests.Test_Insert",
 *		"org.onap.music.mdbc.tests.Test_Delete",
 *		"org.onap.music.mdbc.tests.Test_Transactions"
 *	],
 *	"configs": [
 *		{
 *			"description": "H2 with Cassandra with two connections",
 *			"MDBC_DB_MIXIN": "h2",
 *			"MDBC_MUSIC_MIXIN": "cassandra",
 *			"replicas": "0,1",
 *			"music_keyspace": "mdbctest1",
 *			"music_address": "localhost",
 *			"music_rfactor": "1",
 *			"connections": [
 *				{
 *					"name": "Connection 0",
 *					"url": "jdbc:mdbc:mem:db0",
 *					"user": "",
 *					"password": "",
 *					"myid": "0"
 *				},
 *				{
 *					"name": "Connection 1",
 *					"url": "jdbc:mdbc:mem:db1",
 *					"user": "",
 *					"password": "",
 *					"myid": "1"
 *				}
 *			]
 *		},
 *		{
 *			"description": "H2 with Cassandra2 with three connections",
 *			"MDBC_DB_MIXIN": "h2",
 *			"MDBC_MUSIC_MIXIN": "cassandra2",
 *			"replicas": "0,1,2",
 *			"music_keyspace": "mdbctest2",
 *			"music_address": "localhost",
 *			"music_rfactor": "1",
 *			"user": "",
 *			"password": "",
 *			"connections": [
 *				{
 *					"name": "Connection 0",
 *					"url": "jdbc:mdbc:mem:db0",
 *					"myid": "0"
 *				},
 *				{
 *					"name": "Connection 1",
 *					"url": "jdbc:mdbc:mem:db1",
 *					"myid": "1"
 *				},
 *				{
 *					"name": "Connection 2",
 *					"url": "jdbc:mdbc:mem:db2",
 *					"myid": "2"
 *				}
 *			]
 *		},
 *		{
 *			"description": "H2 Server with Cassandra2 with two connections",
 *			"MDBC_DB_MIXIN": "h2server",
 *			"MDBC_MUSIC_MIXIN": "cassandra2",
 *			"replicas": "0,1",
 *			"music_keyspace": "mdbctest3",
 *			"music_address": "localhost",
 *			"music_rfactor": "1",
 *			"connections": [
 *				{
 *					"name": "Connection 0",
 *					"url": "jdbc:mdbc:tcp://localhost/mdbc0",
 *					"user": "",
 *					"password": "",
 *					"myid": "0"
 *				},
 *				{
 *					"name": "Connection 1",
 *					"url": "jdbc:mdbc:tcp://localhost/mdbc1",
 *					"user": "",
 *					"password": "",
 *					"myid": "1"
 *				}
 *			]
 *		},
 *		{
 *			"description": "MySQL with Cassandra2 with two connections",
 *			"MDBC_DB_MIXIN": "mysql",
 *			"MDBC_MUSIC_MIXIN": "cassandra2",
 *			"replicas": "0,1,2",
 *			"music_keyspace": "mdbctest4",
 *			"music_address": "localhost",
 *			"music_rfactor": "1",
 *			"user": "root",
 *			"password": "abc123",
 *			"connections": [
 *				{
 *					"name": "Connection 0",
 *					"url": "jdbc:mdbc://127.0.0.1:3306/mdbc",
 *					"myid": "0"
 *				},
 *				{
 *					"name": "Connection 1",
 *					"url": "jdbc:mdbc://127.0.0.1:3306/mdbc2",
 *					"myid": "1"
 *				}
 *			]
 *		},
 *		{
 *			"description": "H2 (DB #1) and MySQL (DB #2) with Cassandra2",
 *			"MDBC_MUSIC_MIXIN": "cassandra2",
 *			"replicas": "0,1",
 *			"music_keyspace": "mdbctest5",
 *			"music_address": "localhost",
 *			"music_rfactor": "1",
 *			"connections": [
 *				{
 *					"name": "Connection 0",
 *					"MDBC_DB_MIXIN": "h2",
 *					"url": "jdbc:mdbc:mem:db9",
 *					"user": "",
 *					"password": "",
 *					"myid": "0"
 *				},
 *				{
 *					"name": "Connection 1",
 *					"MDBC_DB_MIXIN": "mysql",
 *					"url": "jdbc:mdbc://127.0.0.1:3306/mdbc3",
 *					"user": "root",
 *					"password": "abc123",
 *					"myid": "1"
 *				}
 *			]
 *		}
 *	]
 * }
 * </pre>
 */
package org.onap.music.mdbc.tests;
