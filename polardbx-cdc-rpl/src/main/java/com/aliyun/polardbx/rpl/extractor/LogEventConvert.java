/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.rpl.extractor;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.XATransactionType;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent.ColumnInfo;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMetaCache;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.Types;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * ??????{@linkplain LogEvent}?????????Entry???????????????
 *
 * @author jianghang 2013-1-17 ??????02:41:14
 * @version 1.0.0
 */
@Slf4j
public class LogEventConvert {

    public static final String ISO_8859_1 = "ISO-8859-1";
    public static final int TINYINT_MAX_VALUE = 256;
    public static final int SMALLINT_MAX_VALUE = 65536;
    public static final int MEDIUMINT_MAX_VALUE = 16777216;
    public static final long INTEGER_MAX_VALUE = 4294967296L;
    public static final BigInteger BIGINT_MAX_VALUE = new BigInteger("18446744073709551616");
    public static final int version = 1;
    public static final String BEGIN = "BEGIN";
    public static final String COMMIT = "COMMIT";

    protected static final String MYSQL = "mysql";
    protected static final String HA_HEALTH_CHECK = "ha_health_check";
    protected static final String DRDS_SYSTEM_MYSQL_HEARTBEAT = "__drds__system__mysql__heartbeat__";

    protected TableMetaCache tableMetaCache;

    protected String binlogFileName = "";
    protected String charset = "utf8";
    protected boolean filterQueryDml = true;
    protected boolean filterRowsQuery = false;
    // ????????????table?????????????????????,??????????????????????????????????????????,issue 92
    protected boolean filterTableError = false;
    protected boolean firstBinlogFile = true;
    protected HostType srcHostType;
    protected HostInfo metaHostInfo;
    protected BaseFilter filter;

    protected FieldMeta rdsImplicitIDFieldMeta;
    protected TableMeta rdsHeartBeatTableMeta;

    public LogEventConvert(HostInfo metaHostInfo, BaseFilter filter, BinlogPosition startBinlogPosition, HostType srcHostType) {
        this.metaHostInfo = metaHostInfo;
        this.filter = filter;
        this.binlogFileName = startBinlogPosition.getFileName();
        this.srcHostType = srcHostType;
    }

    public void refreshState() {
        firstBinlogFile = true;
    }

    public void init() throws Exception {
        try {
            this.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
            DataSource dataSource = DataSourceUtil.createDruidMySqlDataSource(metaHostInfo.isUsePolarxPoolCN(),
                metaHostInfo.getHost(),
                metaHostInfo.getPort(),
                "",
                metaHostInfo.getUserName(),
                metaHostInfo.getPassword(),
                "",
                1,
                2,
                null,
                null);
                Connection conn = dataSource.getConnection();
                MysqlConnection connection = new MysqlConnection(conn);
                this.tableMetaCache = new TableMetaCache(connection);
            initMeta();
        } catch (Throwable e) {
            log.error("LogEventConvert init failed, metaHostInfo: {}, {}", e, JSON.toJSONString(metaHostInfo));
            throw e;
        }
    }

    protected void initMeta() {
        rdsImplicitIDFieldMeta =
            new FieldMeta(RplConstants.RDS_IMPLICIT_ID, "long", false, true, null, true);

        // ??????rds?????????mysql.ha_health_check????????????
        // ??????RDS???????????????????????????,??????mock??????tableMeta
        FieldMeta idMeta = new FieldMeta("id", "bigint(20)", true, false, "0", false);
        FieldMeta typeMeta = new FieldMeta("type", "char(1)", false, true, "0", false);// type?????????
        rdsHeartBeatTableMeta = new TableMeta(MYSQL, HA_HEALTH_CHECK, Arrays.asList(idMeta, typeMeta));
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public void setBinlogFileName(String binlogFileName) {
        this.binlogFileName = binlogFileName;
    }

    public MySQLDBMSEvent parse(LogEvent logEvent, boolean isSeek) throws Exception {
        if (logEvent == null || logEvent instanceof UnknownLogEvent) {
            return null;
        }

        int DBMSAction = logEvent.getHeader().getType();
        switch (DBMSAction) {
        // canal ?????? binlog ??????????????? context (LogContext ??????)???????????? FormatDescriptionLogEvent
        // formatDescription ????????? binlog ????????? CRC32 ????????????FormatDescriptionLogEvent ???
        // checksumAlg ????????????
        // ????????? binlog ???????????? checksumAlg ???????????? LogDecoder ??? 64 ?????????
        // ?????? binlog ??????????????????????????? ROTATE_EVENT??????????????? FORMAT_DESCRIPTION_EVENT???
        // ?????? binlog ??????????????? ROTATE_EVENT ?????? fileName ?????????
        // ROTATE_EVENT ????????? checksumAlg ?????????FORMAT_DESCRIPTION_EVENT ?????? checksumAlg ?????????
        // ????????? binlog ????????? CRC32????????????
        // canal ??????????????? ROTATE_EVENT ?????? fileName ????????????????????? CRC32
        // ???????????? 4 ?????????????????? fileName ????????????????????? LogDecoder ??? 64 ?????????
        // canal ???????????? FORMAT_DESCRIPTION_EVENT ??????????????????????????? checksumAlg???????????? context ??????
        // formatDescription ???????????????????????????
        // ????????? binlog ????????? CRC32???????????????????????? binlogFileName ???????????????????????????????????? event ?????????????????????
        // ???????????? binlog ??????????????????????????????????????? binlog ???????????????????????? binlogFileName ???????????????
        case LogEvent.ROTATE_EVENT:
            if (!firstBinlogFile) {
                binlogFileName = ((RotateLogEvent) logEvent).getFilename();
            }
            break;
        case LogEvent.FORMAT_DESCRIPTION_EVENT:
            firstBinlogFile = false;
            break;
        case LogEvent.QUERY_EVENT:
            return parseQueryEvent((QueryLogEvent) logEvent, isSeek);
        case LogEvent.XID_EVENT:
            return parseXidEvent((XidLogEvent) logEvent);
        case LogEvent.TABLE_MAP_EVENT:
            break;
        case LogEvent.WRITE_ROWS_EVENT_V1:
        case LogEvent.WRITE_ROWS_EVENT:
            return parseRowsEvent((WriteRowsLogEvent) logEvent);
        case LogEvent.UPDATE_ROWS_EVENT_V1:
        case LogEvent.UPDATE_ROWS_EVENT:
            return parseRowsEvent((UpdateRowsLogEvent) logEvent);
        case LogEvent.DELETE_ROWS_EVENT_V1:
        case LogEvent.DELETE_ROWS_EVENT:
            return parseRowsEvent((DeleteRowsLogEvent) logEvent);
        case LogEvent.ROWS_QUERY_LOG_EVENT:
            return parseRowsQueryEvent((RowsQueryLogEvent) logEvent);
        case LogEvent.ANNOTATE_ROWS_EVENT:
            return parseAnnotateRowsEvent((AnnotateRowsEvent) logEvent);
        case LogEvent.USER_VAR_EVENT:
            return parseUserVarLogEvent((UserVarLogEvent) logEvent);
        case LogEvent.INTVAR_EVENT:
            return parseIntrvarLogEvent((IntvarLogEvent) logEvent);
        case LogEvent.RAND_EVENT:
            return parseRandLogEvent((RandLogEvent) logEvent);
        case LogEvent.HEARTBEAT_LOG_EVENT:
            return null;
        default:
            break;
        }

        return null;
    }

    public void reset() {
        tableMetaCache.reset();
    }

    protected MySQLDBMSEvent parseQueryEvent(QueryLogEvent event, boolean isSeek) {
        String queryString = event.getQuery();
        if (StringUtils.endsWithIgnoreCase(queryString, BEGIN)) {
            DBMSTransactionBegin transactionBegin = createTransactionBegin(event.getSessionId());
            return new MySQLDBMSEvent(transactionBegin, createPosition(event.getHeader()));
        } else if (StringUtils.endsWithIgnoreCase(queryString, COMMIT)) {
            DBMSTransactionEnd transactionEnd = createTransactionEnd(0L);
            return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()));
        } else {
            // DDL????????????
            // ????????????????????????queryString???????????????
            HashMap<String, String> ddlInfo = Maps.newHashMap();

            // ?????? sql ??????
            if (StringUtils.startsWithIgnoreCase(StringUtils.trim(queryString), "flush")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(queryString), "grant")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(queryString), "create user")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(queryString), "drop user")) {
                return null;
            }
            List<DdlResult> resultList = DruidDdlParser.parse(queryString, event.getDbName());
            if (CollectionUtils.isEmpty(resultList)) {
                if(!(queryString.startsWith("/*!") && queryString.endsWith("*/"))) {
                    log.error("DDL result list is empty. Raw query string: {}, db: {}", queryString, event.getDbName());
                }
                return null;
            }
            DdlResult result = resultList.get(0);
            DBMSAction action = result.getType();

            // ?????? filter ??? rewriteDbs ?????? dbName

            // ??????????????? DDL: use db1, create table tb1(id int)
            // event.getDbName() == result.getSchemaName() == db1

            // ???????????? DDL:
            // ???????????? use db1, create db2.tb1(id int)
            // ??????event.getDbName() == db1???result.getSchemaName() == db2;

            // ?????? create database db1, drop database db1:
            // ?????????????????????????????? event.getDbName() == result.getSchemaName() == db1

            // getRewriteDb ???????????????????????? sql,
            // ?????????????????? use db2, create table db1.tb1(id int),
            // ?????????????????? use db1, create table tb1(id int)
            // ??? result.getSchemaName() == db1,
            // mysql ???????????????????????????use result.getSchemaName()???create table tb1(id int);

            // ?????????getRewriteDb ??? create database ??? drop database ????????????
            // ???????????? mysql ????????????????????? rewriteDbs: <db1, db2>???
            // ?????? sql: create database db1??????????????????????????????????????? create database db2???
            // ???????????? create database db1???

            // mysql ?????????????????????????????? ddl???????????? event.getDbName() ????????? rewriteDb ????????????
            // ??????????????? result.getSchemaName()???
            // ???????????????????????????????????? result.getSchemaName() ??? rewriteDb ????????????????????? polarx ??? mysql ???????????????
            String rewriteDbName = filter.getRewriteDb(event.getDbName(), action);
            if (filter.ignoreEvent(rewriteDbName, result.getTableName(), action, event.getServerId())) {
                return null;
            }

            // ?????? ddl????????? DDL ????????????????????????????????? db?????? result.getSchemaName()
            if (!StringUtils.equalsIgnoreCase(event.getDbName(), result.getSchemaName())) {
                rewriteDbName = filter.getRewriteDb(result.getSchemaName(), action);
            }

            // ?????? create database, drop database?????????????????????????????? DDL ????????? db ????????????
            // ???????????? sql: create databases db1 ??????????????????????????????????????? use db1
            if (action == DBMSAction.CREATEDB || action == DBMSAction.DROPDB) {
                rewriteDbName = "";
            }

            // ???????????? tableMetaCache
            BinlogPosition position = createPosition(event.getHeader());
            tableMetaCache.apply(position, rewriteDbName, queryString);

            // ????????????
            DefaultQueryLog queryEvent = new DefaultQueryLog(rewriteDbName,
                queryString,
                new java.sql.Timestamp(event.getHeader().getWhen() * 1000),
                event.getErrorCode(),
                result.getType());

            // ??? ddl ??????????????? set ??? optionValue
            if (!ddlInfo.isEmpty()) {
                queryEvent.setOptionValue(DefaultQueryLog.ddlInfo, ddlInfo);
            }

            MySQLDBMSEvent mySQLDBMSEvent = new MySQLDBMSEvent(queryEvent, createPosition(event.getHeader()));

            // ????????? XA ???????????????
            DBMSXATransaction xaTransaction = getXaTransaction(queryString);
            if (xaTransaction != null) {
                mySQLDBMSEvent.setXaTransaction(xaTransaction);
            }

            return mySQLDBMSEvent;
        }
    }

    protected MySQLDBMSEvent parseRowsQueryEvent(RowsQueryLogEvent event) {
        if (filterQueryDml && filterRowsQuery) {
            return null;
        }
        // mysql5.6?????????????????????binlog-rows-query-log-events=1????????????????????????DML??????
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildQueryEntry(queryString, event.getHeader(), DBMSAction.ROWQUERY);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseAnnotateRowsEvent(AnnotateRowsEvent event) {
        if (filterQueryDml) {
            return null;
        }
        // mariaDb?????????????????????binlog_annotate_row_events=true????????????????????????DML??????
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildQueryEntry(queryString, event.getHeader(), null);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseUserVarLogEvent(UserVarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseIntrvarLogEvent(IntvarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseRandLogEvent(RandLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseXidEvent(XidLogEvent event) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(event.getXid());
        return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()));
    }

    protected MySQLDBMSEvent parseRowsEvent(RowsLogEvent event) {
        try {
            TableMapLogEvent table = event.getTable();
            if (table == null) {
                // tableId????????????????????????
                throw new TableIdNotFoundException("not found tableId:" + event.getTableId());
            }

            DBMSAction action = null;
            int type = event.getHeader().getType();
            if (LogEvent.WRITE_ROWS_EVENT_V1 == type || LogEvent.WRITE_ROWS_EVENT == type) {
                action = DBMSAction.INSERT;
            } else if (LogEvent.UPDATE_ROWS_EVENT_V1 == type || LogEvent.UPDATE_ROWS_EVENT == type) {
                action = DBMSAction.UPDATE;
            } else if (LogEvent.DELETE_ROWS_EVENT_V1 == type || LogEvent.DELETE_ROWS_EVENT == type) {
                action = DBMSAction.DELETE;
            } else {
                throw new CanalParseException("unsupport event type :" + event.getHeader().getType());
            }

            // ?????? filter ??? rewriteDbs ?????? DbName
            String rewriteDbName = filter.getRewriteDb(table.getDbName(), action);

            // ????????????
            if (filter.ignoreEvent(rewriteDbName, table.getTableName(), action, event.getServerId())) {
                return null;
            }

            if (isRDSHeartBeat(rewriteDbName, table.getTableName())) {
                return null;
            }

            BinlogPosition position = createPosition(event.getHeader());
            RowsLogBuffer buffer = event.getRowsBuf(charset);
            BitSet columns = event.getColumns();
            BitSet changeColumns = event.getChangeColumns(); // ???????????????,???????????????binlog????????????,mysql full image????????????????????????
            boolean tableError = false;
            TableMeta tableMeta = getTableMeta(rewriteDbName, table.getTableName());
            if (tableMeta == null) {
                tableError = true;
                if (!filterTableError) {
                    throw new CanalParseException(
                        "not found [" + rewriteDbName + "." + table.getTableName() + "] in db , pls check!");
                }
            }

            // check table fileds count????????????????????????
            int columnSize = event.getTable().getColumnCnt();

            // ???????????????
            List<DBMSColumn> dbmsColumns = Lists.newArrayList();
            List<FieldMeta> fieldMetas = tableMeta.getFields();
            // ????????????canal?????????,??????DDL????????????????????????,????????????????????????binlog?????????
            int size = fieldMetas.size();
            if (columnSize < size) {
                size = columnSize;
            }
            for (int i = 0; i < size; i++) {
                FieldMeta fieldMeta = fieldMetas.get(i);
                // ??????????????????sqlType=0??????
                DefaultColumn column = new DefaultColumn(fieldMeta.getColumnName(),
                    i,
                    Types.OTHER,
                    fieldMeta.isUnsigned(),
                    fieldMeta.isNullable(),
                    fieldMeta.isKey(),
                    fieldMeta.isUnique());
                dbmsColumns.add(column);
            }

            DefaultRowChange rowChange = new DefaultRowChange(action,
                rewriteDbName,
                table.getTableName(),
                new DefaultColumnSet(dbmsColumns));
            BitSet actualChangeColumns = new BitSet(columnSize); // ???????????????update??????????????????????????????????????????
            rowChange.setChangeColumnsBitSet(actualChangeColumns);

            while (buffer.nextOneRow(columns)) {
                // ??????row??????
                if (DBMSAction.INSERT == action) {
                    // insert???????????????before?????????
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else if (DBMSAction.DELETE == action) {
                    // delete???????????????before?????????
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else {
                    // update????????????before/after
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                    if (!buffer.nextOneRow(changeColumns)) {
                        break;
                    }

                    parseOneRow(rowChange, event, buffer, changeColumns, true, tableMeta, actualChangeColumns);
                }
            }

            MySQLDBMSEvent dbmsEvent = new MySQLDBMSEvent(rowChange, position);
            if (tableError) {
                log.warn("table parser error : " + rowChange.toString());
                return null;
            } else {
                return dbmsEvent;
            }
        } catch (Exception e) {
            throw new CanalParseException("parse row data failed.", e);
        }
    }

    protected void parseOneRow(DefaultRowChange rowChange, RowsLogEvent event, RowsLogBuffer buffer, BitSet cols,
                               boolean isAfter, TableMeta tableMeta,
                               BitSet actualChangeColumns) throws UnsupportedEncodingException {
        int columnCnt = event.getTable().getColumnCnt();
        ColumnInfo[] columnInfo = event.getTable().getColumnInfo();

        DefaultRowData rowData = new DefaultRowData(columnCnt);
        for (int i = 0; i < columnCnt; i++) {
            ColumnInfo info = columnInfo[i];
            // mysql 5.6????????????nolob/mininal??????,??????????????????????????????,??????????????????
            if (!cols.get(i)) {
                continue;
            }

            FieldMeta fieldMeta = tableMeta.getFields().get(i);
            // fixed issue
            // https://github.com/alibaba/canal/issues/66???????????????binary/varbinary????????????????????????
            boolean isBinary = StringUtils.containsIgnoreCase(fieldMeta.getColumnType(), "VARBINARY")
                || StringUtils.containsIgnoreCase(fieldMeta.getColumnType(), "BINARY");
            // ?????????????????? charset, ?????????????????????????????? charset, ???????????? charset
            String charset = StringUtils.isNotBlank(fieldMeta.getCharset()) ? fieldMeta
                .getCharset() : tableMeta.getCharset();
            String javaCharset = CharsetConversion.getJavaCharset(charset);
            buffer.nextValue(info.type, info.meta, isBinary, javaCharset);

            int javaType = buffer.getJavaType();
            Serializable dataValue = null;
            if (!buffer.isNull()) {
                final Serializable value = buffer.getValue();
                // ??????????????????
                switch (javaType) {
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.BIGINT:
                    // ??????unsigned??????
                    Number number = (Number) value;
                    if (fieldMeta != null && fieldMeta.isUnsigned() && number.longValue() < 0) {
                        switch (buffer.getLength()) {
                        case 1: /* MYSQL_TYPE_TINY */
                            dataValue = String.valueOf(Integer.valueOf(TINYINT_MAX_VALUE + number.intValue()));
                            javaType = Types.SMALLINT; // ?????????????????????
                            break;

                        case 2: /* MYSQL_TYPE_SHORT */
                            dataValue = String.valueOf(Integer.valueOf(SMALLINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // ?????????????????????
                            break;

                        case 3: /* MYSQL_TYPE_INT24 */
                            dataValue = String
                                .valueOf(Integer.valueOf(MEDIUMINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // ?????????????????????
                            break;

                        case 4: /* MYSQL_TYPE_LONG */
                            dataValue = String.valueOf(Long.valueOf(INTEGER_MAX_VALUE + number.longValue()));
                            javaType = Types.BIGINT; // ?????????????????????
                            break;

                        case 8: /* MYSQL_TYPE_LONGLONG */
                            dataValue = BIGINT_MAX_VALUE.add(BigInteger.valueOf(number.longValue())).toString();
                            javaType = Types.DECIMAL; // ??????????????????????????????????????????
                            break;
                        default:
                            break;
                        }
                    } else {
                        // ?????????number???????????????valueof??????
                        dataValue = String.valueOf(value);
                    }
                    break;
                case Types.REAL: // float
                case Types.DOUBLE: // double
                    // ?????????number???????????????valueof??????
                    dataValue = String.valueOf(value);
                    break;
                case Types.BIT:// bit
                    // ?????????byte[]??????,???????????????????????????,???????????????????????????
                    dataValue = value;
                    break;
                case Types.DECIMAL:
                    dataValue = ((BigDecimal) value).toPlainString();
                    break;
                case Types.TIMESTAMP:
                    // ?????????????????????
                    // String v = value.toString();
                    // v = v.substring(0, v.length() - 2);
                    // columnBuilder.setValue(v);
                    // break;
                case Types.TIME:
                case Types.DATE:
                    // ????????????year
                    dataValue = value.toString();
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    // fixed text encoding
                    // https://github.com/AlibabaTech/canal/issues/18
                    // mysql binlog???blob/text????????????blob?????????????????????table
                    // meta??????????????????text
                    if (fieldMeta != null && isText(fieldMeta.getColumnType())) {
                        dataValue = new String((byte[]) value, javaCharset);
                        javaType = Types.CLOB;
                    } else {
                        // byte?????????????????????iso-8859-1?????????????????????????????????
                        dataValue = (byte[]) value;
                        javaType = Types.BLOB;
                    }
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                    dataValue = value.toString();
                    break;
                default:
                    dataValue = value.toString();
                }
            }

            // ?????????1??????
            rowData.setRowValue(i + 1, dataValue);
            // ????????????sqlType
            DBMSColumn dbmsColumn = rowChange.getColumns().get(i);
            if (dbmsColumn instanceof DefaultColumn && dbmsColumn.getSqlType() == Types.OTHER) {
                ((DefaultColumn) dbmsColumn).setSqlType(javaType);
            }
        }

        if (isAfter) {
            rowChange.addChangeData(rowData);
            // ?????????????????????
            DBMSRowData beforeRowData = rowChange.getRowData(rowChange.getRowSize());
            buildChangeColumns(beforeRowData, rowData, columnCnt, actualChangeColumns);
        } else {
            rowChange.addRowData(rowData);
        }
    }

    protected void buildChangeColumns(DBMSRowData beforeRowData, DBMSRowData afterRowData, int size,
                                      BitSet changeColumns) {
        for (int i = 1; i <= size; i++) {
            Serializable before = beforeRowData.getRowValue(i);
            Serializable after = afterRowData.getRowValue(i);

            boolean check = isUpdate(before, after);
            if (check) {
                changeColumns.set(i - 1, true);
            }
        }

    }

    protected boolean isUpdate(Serializable before, Serializable after) {
        if (before == null && after == null) {
            return false;
        } else if (before != null && after != null && isEqual(before, after)) {
            return false;
        }

        // ??????nolob/minial?????????,???????????????before??????,??????????????????
        return true;
    }

    private boolean isEqual(Serializable before, Serializable after) {
        if (before instanceof byte[] && after instanceof byte[]) {
            return Arrays.equals((byte[]) before, (byte[]) after);
        } else {
            return before.equals(after);
        }
    }

    protected MySQLDBMSEvent buildQueryEntry(String queryString, LogHeader logHeader, DBMSAction action) {
        DefaultQueryLog queryLog = new DefaultQueryLog(null,
            queryString,
            new java.sql.Timestamp(logHeader.getWhen() * 1000),
            0,
            action);

        return new MySQLDBMSEvent(queryLog, createPosition(logHeader));
    }

    protected BinlogPosition createPosition(LogHeader logHeader) {
        return new BinlogPosition(binlogFileName, logHeader.getLogPos(), logHeader.getServerId(),
            logHeader.getWhen()); // ????????????
    }

    protected TableMeta getTableMeta(String dbName, String tbName) {
        try {
            TableMeta tableMeta = tableMetaCache.getTableMeta(dbName, tbName);
            if (tableMeta.getPrimaryFields().isEmpty() && !tableMeta.getUseImplicitPk()) {
                if (srcHostType == HostType.RDS) {
                    // ?????? polarx ????????????
                    log.info("Add implicit id {} for table {}.{}", RplConstants.RDS_IMPLICIT_ID, dbName, tbName);
                    tableMeta.addFieldMeta(rdsImplicitIDFieldMeta);
                    tableMeta.setUseImplicitPk(true);
                }
            }
            return tableMeta;
        } catch (Throwable e) {
            String message = ExceptionUtils.getRootCauseMessage(e);
            if (filterTableError) {
                if (StringUtils.contains(message, "errorNumber=1146")
                    && StringUtils.contains(message, "doesn't exist")) {
                    return null;
                }
            }

            throw new CanalParseException(e);
        }
    }

    protected boolean isText(String columnType) {
        return "LONGTEXT".equalsIgnoreCase(columnType) || "MEDIUMTEXT".equalsIgnoreCase(columnType)
            || "TEXT".equalsIgnoreCase(columnType) || "TINYTEXT".equalsIgnoreCase(columnType);
    }

    protected boolean isRDSHeartBeat(String schema, String table) {
        return (MYSQL.equalsIgnoreCase(schema) && HA_HEALTH_CHECK.equalsIgnoreCase(table))
            || DRDS_SYSTEM_MYSQL_HEARTBEAT.equalsIgnoreCase(table);
    }

    public static DBMSTransactionBegin createTransactionBegin(long threadId) {
        DBMSTransactionBegin transactionBegin = new DBMSTransactionBegin();
        transactionBegin.setThreadId(threadId);
        return transactionBegin;
    }

    public static DBMSTransactionEnd createTransactionEnd(long transactionId) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(transactionId);
        return transactionEnd;
    }

    protected String getXid(String queryString, XATransactionType type) throws CanalParseException {
        return queryString.substring(type.getName().length()).trim();
    }

    protected DBMSXATransaction getXaTransaction(String queryString) throws CanalParseException {
        DBMSXATransaction xaTransaction = null;
        if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_START.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_START),
                XATransactionType.XA_START);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_END.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_END),
                XATransactionType.XA_END);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_COMMIT.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_COMMIT),
                XATransactionType.XA_COMMIT);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_ROLLBACK.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_ROLLBACK),
                XATransactionType.XA_ROLLBACK);
        }

        return xaTransaction;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}
