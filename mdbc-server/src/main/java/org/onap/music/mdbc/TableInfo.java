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

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Information about a table in the local database.  It consists of three ordered list, which should all have the
 * same length. A list of column names, a list of DB column types, and a list of booleans specifying which columns are keys.
 * @author Robert P. Eby
 */
public class TableInfo {
	/** An ordered list of the column names in this table */
	public List<String>  columns;
	/** An ordered list of the column types in this table; the types are integers taken from {@link java.sql.Types}. */
	public List<Integer> coltype;
	/** An ordered list of booleans indicating if a column is a primary key column or not. */
	public List<Boolean> iskey;

	/** Construct an (initially) empty TableInfo. */
	public TableInfo() {
		columns  = new ArrayList<String>();
		coltype  = new ArrayList<Integer>();
		iskey    = new ArrayList<Boolean>();
	}
	/**
	 * Check whether the column whose name is <i>name</i> is a primary key column.
	 * @param name the column name
	 * @return true if it is, false otherwise
	 */
	public boolean iskey(String name) {
		for (int i = 0; i < columns.size(); i++) {
			if (this.columns.get(i).equalsIgnoreCase(name))
				return this.iskey.get(i);
		}
		return false;
	}
	/**
	 * Get the type of the column whose name is <i>name</i>.
	 * @param name the column name
	 * @return the column type or Types.NULL
	 */
	public int getColType(String name) {
		for (int i = 0; i < columns.size(); i++) {
			if (this.columns.get(i).equalsIgnoreCase(name))
				return this.coltype.get(i);
		}
		return Types.NULL;
	}
	
	/**
	 * Checks if this table has a primary key
	 * @return
	 */
	public boolean hasKey() {
		for (Boolean b: iskey) {
			if (b) {
				return true;
			}
		}
		return false;
	}
	
	public List<String> getKeyColumns(){
		List<String> keys = new ArrayList<String>();
		int idx = 0;
		for (Boolean b: iskey) {
			if (b) {
				keys.add(this.columns.get(idx));
			}
			idx++;
		}
		return keys;
	}
}
