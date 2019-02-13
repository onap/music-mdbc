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

package org.onap.music.mdbc.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.drop.Drop;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.avatica.util.Quoting;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.fun.SqlInOperator;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserImplFactory;
import org.apache.calcite.sql.parser.impl.SqlParserImpl;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.util.Util;
import org.onap.music.logging.EELFLoggerDelegate;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class QueryProcessor {

    private static EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(QueryProcessor.class);

    public List<String> tables = null;

    public QueryProcessor() {

    }

    protected static SqlParserImplFactory parserImplFactory() {
        return SqlParserImpl.FACTORY;
    }

    protected static SqlParser getSqlParser(String sql) {
        Quoting quoting = Quoting.DOUBLE_QUOTE;
        Casing unquotedCasing = Casing.TO_UPPER;
        Casing quotedCasing = Casing.UNCHANGED;
        SqlConformance conformance = SqlConformanceEnum.DEFAULT;

        return SqlParser.create(sql, SqlParser.configBuilder().setParserFactory(parserImplFactory()).setQuoting(quoting)
                .setUnquotedCasing(unquotedCasing).setQuotedCasing(quotedCasing).setConformance(conformance).build());
    }

    /**
     * 
     * @param query
     * @return map of table name to {@link org.onap.music.mdbc.query.Operation}
     * @throws SqlParseException
     */
    public static Map<String, List<Operation>> parseSqlQuery(String query) throws SqlParseException {
        logger.info(EELFLoggerDelegate.applicationLogger, "Parsing query: "+query);
        Map<String, List<Operation>> tableOpsMap = new HashMap<>();
        //for Create no need to check locks.
        if(query.toUpperCase().startsWith("CREATE"))  {
            logger.error(EELFLoggerDelegate.errorLogger, "CREATE TABLE DDL not currently supported currently.");
            return tableOpsMap;
        }

        /*SqlParser parser = SqlParser.create(query);
		SqlNode sqlNode = parser.parseQuery();*/
        SqlNode sqlNode = getSqlParser(query).parseStmt();

        SqlBasicVisitor<Void> visitor = new SqlBasicVisitor<Void>() {

            public Void visit(SqlCall call) {
                if (call.getOperator() instanceof SqlInOperator) {
                    throw new Util.FoundOne(call);
                }
                return super.visit(call);
            }

        };

        // sqlNode.accept(new SqlAnalyzer());
        sqlNode.accept(visitor);
        switch (sqlNode.getKind()) {
            case INSERT:
                parseInsert((SqlInsert) sqlNode, tableOpsMap);
                break;
            case UPDATE:
                parseUpdate((SqlUpdate) sqlNode, tableOpsMap);
                break;
            case SELECT:
                parseSelect((SqlSelect) sqlNode, tableOpsMap);
                break;
            default:
                logger.error("Unhandled sql query type " + sqlNode.getKind() +" for query " + query);
        }
        return tableOpsMap;
    }

    private static void parseInsert(SqlInsert sqlNode, Map<String, List<Operation>> tableOpsMap) {
        SqlInsert sqlInsert = (SqlInsert) sqlNode;
        String tableName = sqlInsert.getTargetTable().toString();
        //handle insert into select query
        if (sqlInsert.getSource().getKind()==SqlKind.SELECT) {
            parseSelect((SqlSelect) sqlInsert.getSource(), tableOpsMap);
        }
        List<Operation> Ops = tableOpsMap.get(tableName);
        if (Ops == null)
            Ops = new ArrayList<>();
        Ops.add(Operation.INSERT);
        tableOpsMap.put(tableName, Ops);
    }
    
    private static void parseUpdate(SqlUpdate sqlNode, Map<String, List<Operation>> tableOpsMap) {
        SqlUpdate sqlUpdate = (SqlUpdate) sqlNode;
        String tableName = sqlUpdate.getTargetTable().toString();
        List<Operation> Ops = tableOpsMap.get(tableName);
        if (Ops == null)
            Ops = new ArrayList<>();
        Ops.add(Operation.UPDATE);
        tableOpsMap.put(tableName, Ops);
    }
    
    private static void parseSelect(SqlSelect sqlNode, Map<String, List<Operation>> tableOpsMap ) {
        SqlSelect sqlSelect = (SqlSelect) sqlNode;
        SqlNodeList selectList = sqlSelect.getSelectList();
        String tables = sqlSelect.getFrom().toString();
        String[] tablesArr = tables.split(",");

        for (String table : tablesArr) {

            String tableName = null;
            if(table.contains("`")) {
                String[] split = table.split("`");
                tableName = split[1];
            } else {
                tableName = table;
            }
            List<Operation> Ops = tableOpsMap.get(tableName);
            if (Ops == null) Ops = new ArrayList<>();
            Ops.add(Operation.SELECT);
            tableOpsMap.put(tableName, Ops);
        }
    }

    protected static String getTableWithSchemaIfExists(Table table){
        StringBuilder tableName=new StringBuilder();
        if(table.getSchemaName()!=null && !table.getSchemaName().isEmpty()){
            tableName.append(table.getSchemaName())
                .append(".");
        }
        tableName.append(table.getName());
        return tableName.toString();
    }

    @Deprecated
    public static Map<String, List<String>> extractTableFromQuery(String sqlQuery) {
        List<String> tables = null;
        Map<String, List<String>> tableOpsMap = new HashMap<>();
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(sqlQuery);
            if (stmt instanceof Insert) {
                Insert s = (Insert) stmt;
                String tbl = getTableWithSchemaIfExists(s.getTable());
                List<String> Ops = tableOpsMap.get(tbl);
                if (Ops == null)
                    Ops = new ArrayList<>();
                Ops.add(Operation.INSERT.getOperation());
                tableOpsMap.put(tbl, Ops);
                logger.debug(EELFLoggerDelegate.applicationLogger, "Inserting into table: " + tbl);
            } else {
                String tbl;
                String where = "";
                if (stmt instanceof Update) {
                    Update u = (Update) stmt;
                    tbl = getTableWithSchemaIfExists(u.getTables().get(0));
                    List<String> Ops = tableOpsMap.get(tbl);
                    if (Ops == null)
                        Ops = new ArrayList<>();
                    if (u.getWhere() != null) {
                        where = u.getWhere().toString();
                        logger.debug(EELFLoggerDelegate.applicationLogger, "Updating table: " + tbl);
                        Ops.add(Operation.UPDATE.getOperation());
                    } else {
                        Ops.add(Operation.TABLE.getOperation());
                    }
                    tableOpsMap.put(tbl, Ops);
                } else if (stmt instanceof Delete) {
                    Delete d = (Delete) stmt;
                    tbl = getTableWithSchemaIfExists(d.getTable());
                    List<String> Ops = tableOpsMap.get(tbl);
                    if (Ops == null)
                        Ops = new ArrayList<>();
                    if (d.getWhere() != null) {
                        where = d.getWhere().toString();
                        Ops.add(Operation.DELETE.getOperation());
                    } else {
                        Ops.add(Operation.TABLE.getOperation());
                    }
                    tableOpsMap.put(tbl, Ops);
                    logger.debug(EELFLoggerDelegate.applicationLogger, "Deleting from table: " + tbl);
                } else if (stmt instanceof Select) {
                    TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                    tables = tablesNamesFinder.getTableList(stmt);
                    for (String table : tables) {
                        List<String> Ops = tableOpsMap.get(table);
                        if (Ops == null)
                            Ops = new ArrayList<>();
                        Ops.add(Operation.SELECT.getOperation());
                        tableOpsMap.put(table, Ops);
                    }
                } else if (stmt instanceof CreateTable) {
                    CreateTable ct = (CreateTable) stmt;
                    List<String> Ops = new ArrayList<>();
                    Ops.add(Operation.TABLE.getOperation());
                    tbl = getTableWithSchemaIfExists(ct.getTable());
                    tableOpsMap.put(tbl, Ops);
                } else if (stmt instanceof Drop) {
                    Drop ct = (Drop) stmt;
                    List<String> Ops = new ArrayList<>();
                    Ops.add(Operation.TABLE.getOperation());
                    tbl = getTableWithSchemaIfExists(ct.getName());
                    tableOpsMap.put(tbl, Ops);
                } else {
                    logger.error(EELFLoggerDelegate.errorLogger, "Not recognized sql type:" + stmt.getClass());
                    tbl = "";
                }
            }
        } catch (JSQLParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return tableOpsMap;
    }

}
