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

package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.LowerCaseTableNameVariables;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ITableMetaDelegate;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.RowsLogEventRebuilder;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.TableMapEventRebuilder;
import com.aliyun.polardbx.binlog.extractor.log.DDLEvent;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.format.BinlogBuilder;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.field.SimpleField;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.CharsetConversion;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_USE_HISTORY_TABLE_FIRST;

/**
 * @author chengjin.lyf on 2020/8/7 3:13 下午
 * @since 1.0.25
 */
public class RebuildEventLogFilter implements LogEventFilter<TransactionGroup> {

    private static final Logger logger = LoggerFactory.getLogger(RebuildEventLogFilter.class);
    private static ThreadLocal<AutoExpandBuffer> localBuffer = new ThreadLocal<AutoExpandBuffer>();
    private int serviceId;
    private int offset = 5;
    private ITableMetaDelegate delegate;
    private String defaultCharset;
    private LogDecoder logDecoder = new LogDecoder();

    private EventAcceptFilter eventAcceptFilter;

    private FormatDescriptionLogEvent fde;

    private Set<String> cdcSchemaSet;

    public RebuildEventLogFilter(int serviceId, EventAcceptFilter eventAcceptFilter, Set<String> cdcSchemaSet) {
        this.serviceId = serviceId;
        this.eventAcceptFilter = eventAcceptFilter;
        this.cdcSchemaSet = cdcSchemaSet;
        logDecoder.handle(LogEvent.QUERY_EVENT);
        logDecoder.handle(LogEvent.UPDATE_ROWS_EVENT);
        logDecoder.handle(LogEvent.UPDATE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.WRITE_ROWS_EVENT);
        logDecoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.DELETE_ROWS_EVENT);
        logDecoder.handle(LogEvent.DELETE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.TABLE_MAP_EVENT);
    }

    private boolean reformat(LogEvent event, HandlerContext context, String virtualTSO) throws Exception {
        int type = event.getHeader().getType();

        switch (type) {
        case LogEvent.QUERY_EVENT:
            handleQueryLog((QueryLogEvent) event, virtualTSO, context);
            return false;
        case LogEvent.UPDATE_ROWS_EVENT_V1:
        case LogEvent.UPDATE_ROWS_EVENT:
            rebuildRowLogEvent((RowsLogEvent) event, LogEvent.UPDATE_ROWS_EVENT);
            break;
        case LogEvent.DELETE_ROWS_EVENT:
        case LogEvent.DELETE_ROWS_EVENT_V1:
            rebuildRowLogEvent((RowsLogEvent) event, LogEvent.DELETE_ROWS_EVENT);
            break;
        case LogEvent.WRITE_ROWS_EVENT:
        case LogEvent.WRITE_ROWS_EVENT_V1:
            rebuildRowLogEvent((RowsLogEvent) event, LogEvent.WRITE_ROWS_EVENT);
            break;
        case LogEvent.TABLE_MAP_EVENT:
            rebuildTableMapEvent((TableMapLogEvent) event);
            break;
        default:
            // should not be here
            return false;
        }
        return true;
    }

    private void rebuildTableMapEvent(TableMapLogEvent tle) throws Exception {
        if (SystemDB.isSys(tle.getDbName())) {
            return;
        }
        LogicTableMeta tableMeta = delegate.compare(tle.getDbName(), tle.getTableName());
        if (logger.isDebugEnabled()) {
            logger.debug("detected un compatible table meta for table map event, will reformat event "
                + tableMeta.getPhySchema() + tableMeta.getPhyTable());
        }
        TableMapEventBuilder tme = TableMapEventRebuilder.convert(tle, serviceId);
        if (!tableMeta.isCompatible()) {
            try {
                rebuildTableMapBuilder(tme, tableMeta);
            } catch (Exception e) {
                TableMapLogEvent.ColumnInfo columnInfo[] = tle.getColumnInfo();
                StringBuilder errorInfo = new StringBuilder();
                for (LogicTableMeta.FieldMetaExt fieldMetaExt : tableMeta.getLogicFields()) {
                    if (fieldMetaExt.getPhyIndex() >= columnInfo.length) {
                        errorInfo
                            .append("not found phy columnIndex " + fieldMetaExt.getPhyIndex() + " with column name : "
                                + fieldMetaExt.getColumnName());
                    }
                    if (fieldMetaExt.getLogicIndex() >= tableMeta.getLogicFields().size()) {
                        errorInfo.append(
                            "not found logic columnIndex " + fieldMetaExt.getLogicIndex() + " with column name : "
                                + fieldMetaExt.getColumnName());
                    }
                }
                logger.error(
                    "rebuild table map error " + tme.getSchema() + "." + tme.getTableName() + " error : " + errorInfo,
                    e);
                throw e;
            }
        }
        tme.setSchema(tableMeta.getLogicSchema());
        tme.setTableName(tableMeta.getLogicTable());
        tle.setNewData(toByte(tme));
        if (logger.isDebugEnabled()) {
            logger.debug("table map event : " + new Gson().toJson(tle.toBytes()));
        }
    }

    private void rebuildRowLogEvent(RowsLogEvent rle, int eventType) {
        if (SystemDB.isSys(rle.getTable().getDbName())) {
            return;
        }
        LogicTableMeta tableMeta = delegate.compare(rle.getTable().getDbName(), rle.getTable().getTableName());
        // 整形只考虑 insert,其他可以不考虑,如果 是全镜像导致下游报错，则全部都需要处理
        if (logger.isDebugEnabled()) {
            logger.debug(
                "detected compatible " + tableMeta.isCompatible() + " table meta for event, will reformat event "
                    + tableMeta.getPhySchema() + tableMeta.getPhyTable());
        }
        try {
            RowEventBuilder reb = RowsLogEventRebuilder.convert(rle, serviceId);
            reb.setServerId(serviceId);
            reb.setEventType(eventType);
            if (!tableMeta.isCompatible()) {
                rebuildRowEventBuilder(tableMeta, reb, rle.getTable());
            }
            rle.setNewData(toByte(reb));
        } catch (Exception e) {
            throw new PolardbxException(" reformat log pos : " + rle.getHeader().getLogPos() + " occur error", e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("row event : " + new Gson().toJson(rle.toBytes()));
        }
    }

    private String buildCommitKey(RowsLogEvent rle, RowEventBuilder reb, TableMapLogEvent table,
                                  LogicTableMeta tableMeta) {
        StringBuilder logBuilder = new StringBuilder();
        RowsLogBuffer logBuffer = new RowsLogBuffer(new LogBuffer(new byte[0], 0, 0), 0, "utf8");
        logBuilder.append(BinlogEventType.valueOf(reb.getEventType()))
            .append("[")
            .append(table.getDbName())
            .append(".")
            .append(table.getTableName())
            .append("]");
        List<RowData> rowDataList = reb.getRowDataList();
        for (RowData rowData : rowDataList) {
            fillLog(logBuilder, rowData.getBiFieldList(), logBuffer, tableMeta, table);
            if (rowData.getAiFieldList() != null) {
                fillLog(logBuilder, rowData.getAiFieldList(), logBuffer, tableMeta, table);
            }
        }
        return logBuilder.toString();
    }

    private void fillLog(StringBuilder logBuilder, List<Field> fields, RowsLogBuffer logBuffer,
                         LogicTableMeta tableMeta, TableMapLogEvent table) {
        logBuilder.append("[");
        int idx = 0;
        List<LogicTableMeta.FieldMetaExt> fieldMetaExts = tableMeta.getLogicFields();
        for (Field f : fields) {
            if (f instanceof SimpleField) {
                SimpleField sf = (SimpleField) f;
                LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetaExts.get(idx);
                if (!fieldMetaExt.isKey() && !fieldMetaExt.isUnique()) {
                    continue;
                }
                try {
                    String charset = getCharset(fieldMetaExt, table.getDbName(), table.getTableName());
                    if (sf.encode().length > 1000) {
                        logBuilder.append("....");
                    } else {
                        String javaCharset = CharsetConversion.getJavaCharset(charset);
                        Serializable data = logBuffer.fetchValue(sf.getFieldType(), sf.getMeta(), false, sf.encode(),
                            javaCharset);
                        logBuilder.append(data);
                    }

                } catch (Exception e) {
                    logBuilder.append("parse error");
                }
            } else {
                logBuilder.append(f.getValue());
            }
            logBuilder.append(",");
            idx++;
        }
        logBuilder.append("]");
    }

    @Override
    public void handle(TransactionGroup event, HandlerContext context) throws Exception {
        Iterator<Transaction> tranIt = event.getTransactionList().iterator();
        LogContext lc = new LogContext();
        lc.setFormatDescription(fde);
        lc.setLogPosition(new LogPosition(""));
        while (tranIt.hasNext()) {
            Transaction transaction = tranIt.next();
            if (transaction.isDescriptionEvent()) {
                fde = transaction.getFdle();
                lc.setFormatDescription(fde);
            }
            Iterator<TxnItemRef> it = transaction.iterator();
            if (it != null) {
                boolean allRemove = true;
                while (it.hasNext()) {
                    TxnItemRef tir = it.next();
                    byte[] bytes = tir.getPayload();
                    LogEvent e = logDecoder.decode(new LogBuffer(bytes, 0, bytes.length), lc);
                    if (!eventAcceptFilter.accept(e)) {
                        it.remove();
                        continue;
                    }
                    if (!reformat(e, context, transaction.getVirtualTSO())) {
                        it.remove();
                        continue;
                    }
                    allRemove = false;
                    tir.setPayload(e.toBytes());
                }
                if (allRemove) {
                    transaction.release();
                }
            }

            if (transaction.isHeartbeat() || transaction.isInstructionCommand()) {
                if (!cdcSchemaSet.contains(transaction.getSourceCdcSchema())) {
                    transaction.release();
                    tranIt.remove();
                    continue;
                }
            }

            if (transaction.isDDL()) {
                try {
                    rebuildDDL(transaction, context);
                } catch (Exception e) {
                    throw new PolardbxException(e);
                }
            }

            if (!transaction.isVisible()) {
                transaction.release();
                tranIt.remove();
                continue;
            }

        }
        if (!event.isEmpty()) {
            context.doNext(event);
        }

    }

    private void handleQueryLog(QueryLogEvent event, String virtualTSO, HandlerContext context) throws Exception {
        String query = event.getQuery();
        if (LogEventUtil.isTransactionEvent(event)) {
            context.doNext(event);
            return;
        }
        if (SystemDB.isSys(event.getDbName())) {
            // ignore 系统库 DDL
            return;
        }

        if (query.toLowerCase().contains("__drds_global_tx_log")) {
            // ignore drds xa 表
            return;
        }

        RuntimeContext rc = context.getRuntimeContext();
        if (rc.getLowerCaseTableNames() == LowerCaseTableNameVariables.LOWERCASE.getValue()) {
            query = query.toLowerCase();
        }

        BinlogPosition position = new BinlogPosition(context.getRuntimeContext().getBinlogFile(),
            event.getLogPos(),
            event.getServerId(),
            event.getWhen());
        position.setRtso(virtualTSO);

        logger.info("receive phy ddl " + query + " for pos " + new Gson().toJson(position));

        boolean useHistoryTableFirst = DynamicApplicationConfig.getBoolean(META_USE_HISTORY_TABLE_FIRST);
        if (useHistoryTableFirst) {
            logger.warn("begin to query ddl sql from history table for db {} and tso {}.", event.getDbName(),
                position.getRtso());
            String tempSql = getPhySqlFromHistoryTable(event.getDbName(), position.getRtso(),
                context.getRuntimeContext().getStorageInstId());
            if (org.apache.commons.lang.StringUtils.isNotBlank(tempSql)) {
                query = tempSql;
                logger.warn("ddl sql in history table is " + query);
            } else {
                logger.warn("ddl sql is not existed in history table, schema name {}, position {}, origin sql {}",
                    event.getDbName(), position.getRtso(), query);
            }
        }

        delegate.apply(position, event.getDbName(), query, null, context.getRuntimeContext());
    }

    private void rebuildDDL(Transaction transaction, HandlerContext context) throws Exception {
        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
        RuntimeContext runtimeContext = context.getRuntimeContext();
        ServerCharactorSet serverCharactorSet = runtimeContext.getServerCharactorSet();
        Integer clientCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetClient());
        Integer connectionCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetConnection());
        Integer serverCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetServer());
        TopologyRecord topologyRecord;
        try {
            topologyRecord = new Gson().fromJson(ddlRecord.getMetaInfo(), TopologyRecord.class);
        } catch (Throwable t) {
            topologyRecord = JsonRepairUtil.repair(ddlRecord);
            if (topologyRecord == null) {
                throw t;
            }
        }
        String dbCharset = null;
        String tbCollation = null;
        if (topologyRecord != null) {
            LogicMetaTopology.LogicTableMetaTopology tableMetas = topologyRecord.getLogicTableMeta();
            if (tableMetas != null) {
                tbCollation = tableMetas.getTableCollation();
            }
            LogicMetaTopology.LogicDbTopology dbTopology = topologyRecord.getLogicDbMeta();
            if (dbTopology != null) {
                dbCharset = dbTopology.getCharset();
            }
        }

        // 用来处理一些异常情况，比如打标sql有问题，或cdc识别不了打标sql，都可以通过该方式进行容错处理
        boolean useHistoryTableFirst = DynamicApplicationConfig.getBoolean(META_USE_HISTORY_TABLE_FIRST);
        if (useHistoryTableFirst) {
            logger.warn("begin to get sql from history table with db {}, and tso{} ", ddlRecord.getSchemaName(),
                ddlEvent.getPosition().getRtso());
            String sql = getLogicSqlFromHistoryTable(ddlRecord.getSchemaName(), ddlEvent.getPosition().getRtso());
            if (StringUtils.isNotBlank(sql)) {
                logger.warn("ddl sql in history table is " + sql);
                ddlEvent.getDdlRecord().setDdlSql(sql);
            } else {
                logger.warn("ddl sql is not existed in history table.");
            }
        }

        String orgDDL = ddlEvent.getDdlRecord().getDdlSql().trim();
        logger.info("begin to apply logic ddl : " + orgDDL + " tso : " + transaction.getVirtualTSO());
        boolean isNormalDDL = true;
        String ddl = "select 1";
        if (orgDDL.startsWith("move") || orgDDL.startsWith("MOVE")) {
            isNormalDDL = false;
        } else {
            ddl = DDLConverter.formatPolarxDDL(orgDDL, dbCharset, tbCollation,
                runtimeContext.getLowerCaseTableNames());
        }

        ddlEvent.getDdlRecord().setDdlSql(ddl);
        logger.info("real apply logic ddl is : " + ddl);
        delegate.applyLogic(ddlEvent.getPosition(),
            ddlEvent.getDdlRecord(),
            ddlEvent.getExt(),
            context.getRuntimeContext());

        if (isNormalDDL) {
            //转换成标准DDL
            ddl = DDLConverter.convertNormalDDL(orgDDL, dbCharset, tbCollation, transaction.getVirtualTSO());
            ddlEvent.setQueryEventBuilder(new QueryEventBuilder(ddlRecord.getSchemaName(),
                ddl,
                clientCharsetId,
                connectionCharsetId,
                serverCharsetId,
                true,
                (int) ddlEvent.getPosition().getTimestamp(),
                serviceId));
            ddlEvent.setCommitKey(ddl);
            ddlEvent.setData(toByte(ddlEvent.getQueryEventBuilder()));
        }
    }

    private void rebuildTableMapBuilder(TableMapEventBuilder tme, LogicTableMeta tableMeta) {
        byte[] typeDef = tme.getColumnDefType();
        byte[][] metaDef = tme.getColumnMetaData();
        BitMap nullBitmap = tme.getNullBitmap();
        List<LogicTableMeta.FieldMetaExt> fieldMetas = tableMeta.getLogicFields();
        int newColSize = fieldMetas.size();
        byte[] newTypeDef = new byte[newColSize];
        byte[][] newMetaDef = new byte[newColSize][];
        BitMap newNullBitMap = new BitMap(newColSize);

        for (LogicTableMeta.FieldMetaExt fieldMetaExt : fieldMetas) {
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();

            if (phyIndex >= 0) {
                newTypeDef[logicIndex] = typeDef[phyIndex];
                newMetaDef[logicIndex] = metaDef[phyIndex];
                newNullBitMap.set(logicIndex, nullBitmap.get(phyIndex));
            } else {
                String charset = getCharset(fieldMetaExt, tme.getSchema(), tme.getTableName());
                String defaultValue = fieldMetaExt.getDefaultValue();
                Field field = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    defaultValue,
                    charset,
                    fieldMetaExt.isNullable());
                if (field == null) {
                    String errorMsg = String.format("not support for add new Field: %s.%s %s",
                        tme.getSchema(),
                        fieldMetaExt.getColumnName(),
                        fieldMetaExt.getColumnType());
                    logger.error(errorMsg);
                    throw new PolardbxException(errorMsg);
                }
                newTypeDef[logicIndex] = (byte) field.getMysqlType().getType();
                newMetaDef[logicIndex] = field.doGetTableMeta();
                newNullBitMap.set(logicIndex, field.isNullable());
            }
        }
        tme.setColumnDefType(newTypeDef);
        tme.setColumnMetaData(newMetaDef);
        tme.setNullBitmap(newNullBitMap);
    }

    private void rebuildRowEventBuilder(LogicTableMeta tableMeta, RowEventBuilder reb, TableMapLogEvent table) {
        List<LogicTableMeta.FieldMetaExt> fieldMetas = tableMeta.getLogicFields();
        reb.setColumnCount(fieldMetas.size());
        List<RowData> rowDataList = reb.getRowDataList();
        int newColSize = fieldMetas.size();
        BitMap columnBitMap = new BitMap(newColSize);
        reb.setColumnsBitMap(columnBitMap);

        List<RowData> newRowDataList = new ArrayList<>();
        for (RowData rowData : rowDataList) {
            RowData newRowData = new RowData();
            // 先处理before image
            processBIImage(fieldMetas, table, rowData, newRowData, reb);
            if (reb.isUpdate()) {
                // 处理 after image
                processAIImage(fieldMetas, rowData, newRowData, reb, table);
            }

            newRowDataList.add(newRowData);
        }
        reb.setRowDataList(newRowDataList);
    }

    private void processBIImage(List<LogicTableMeta.FieldMetaExt> fieldMetas, TableMapLogEvent table,
                                RowData oldRowData, RowData newRowData, RowEventBuilder reb) {
        List<Field> dataField = oldRowData.getBiFieldList();
        List<Field> newBiFieldList = new ArrayList<>(fieldMetas.size());
        BitMap newBiNullBitMap = new BitMap(fieldMetas.size());
        BitMap newColumnBitMap = new BitMap(fieldMetas.size());
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int phyIndex = fieldMetaExt.getPhyIndex();
            int logicIdx = fieldMetaExt.getLogicIndex();
            newColumnBitMap.set(logicIdx, true);
            Field biField;
            if (phyIndex < 0) {
                String charset = getCharset(fieldMetaExt, table.getDbName(), table.getTableName());
                biField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    fieldMetaExt.getDefaultValue(),
                    charset,
                    fieldMetaExt.isNullable());
            } else {
                biField = dataField.get(phyIndex);
            }
            newBiNullBitMap.set(i, biField.isNull());
            newBiFieldList.add(biField);
        }
        newRowData.setBiNullBitMap(newBiNullBitMap);
        newRowData.setBiFieldList(newBiFieldList);
        reb.setColumnsBitMap(newColumnBitMap);
    }

    private void processAIImage(List<LogicTableMeta.FieldMetaExt> fieldMetas, RowData oldRowData, RowData newRowData,
                                RowEventBuilder reb, TableMapLogEvent table) {
        BitMap newAINullBitMap = new BitMap(fieldMetas.size());
        BitMap newAIChangeBitMap = new BitMap(fieldMetas.size());
        List<Field> newAIFiledList = new ArrayList<>();
        BitMap orgAiChangeBitMap = reb.getColumnsChangeBitMap();
        BitMap orgAiNullBitMap = oldRowData.getAiNullBitMap();
        List<Field> orgFieldList = oldRowData.getAiFieldList();
        for (int i = 0; i < fieldMetas.size(); i++) {
            LogicTableMeta.FieldMetaExt fieldMetaExt = fieldMetas.get(i);
            int logicIndex = fieldMetaExt.getLogicIndex();
            int phyIndex = fieldMetaExt.getPhyIndex();
            if (phyIndex < 0) {
                newAIChangeBitMap.set(logicIndex, true);
                String charset = getCharset(fieldMetaExt, table.getDbName(), table.getTableName());
                Field aiField = MakeFieldFactory.makeField(fieldMetaExt.getColumnType(),
                    fieldMetaExt.getDefaultValue(),
                    charset,
                    fieldMetaExt.isNullable());
                newAINullBitMap.set(logicIndex, aiField.isNull());
                if (!aiField.isNull()) {
                    newAIFiledList.add(aiField);
                }
            } else {
                boolean exist = orgAiChangeBitMap.get(phyIndex);
                newAIChangeBitMap.set(logicIndex, exist);
                if (exist) {
                    boolean isNull = orgAiNullBitMap.get(phyIndex);
                    newAINullBitMap.set(logicIndex, isNull);
                    if (!isNull) {
                        newAIFiledList.add(orgFieldList.get(phyIndex));
                    }
                }
            }
        }
        reb.setColumnsChangeBitMap(newAIChangeBitMap);
        newRowData.setAiNullBitMap(newAINullBitMap);
        newRowData.setAiFieldList(newAIFiledList);
    }

    private String getCharset(LogicTableMeta.FieldMetaExt fieldMetaExt, String db, String table) {
        String charset = fieldMetaExt.getCharset();
        if (StringUtils.isBlank(charset)) {
            charset = delegate.findLogic(db, table).getCharset();
        }
        if (StringUtils.isBlank(charset)) {
            charset = defaultCharset;
        }
        return charset;
    }

    private byte[] toByte(BinlogBuilder binlog) throws Exception {
        AutoExpandBuffer buf = getBuffer();
        int size = binlog.write(buf);
        byte[] newBuf = new byte[size];
        System.arraycopy(buf.toBytes(), 0, newBuf, 0, size);
        return newBuf;
    }

    private AutoExpandBuffer getBuffer() {
        AutoExpandBuffer buf = localBuffer.get();
        if (buf == null) {
            buf = new AutoExpandBuffer(1024 * 1024, 1024);
            localBuffer.set(buf);
        }
        buf.reset();
        return buf;
    }

    private String getLogicSqlFromHistoryTable(String dbName, String tso) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = metaJdbcTemplate.queryForList(
            "select ddl from binlog_logic_meta_history where tso = '" + tso + "' and db_name = '" + dbName + "'",
            String.class);
        return list.isEmpty() ? null : list.get(0);
    }

    private String getPhySqlFromHistoryTable(String dbName, String tso, String storageInstId) {
        BinlogPhyDdlHistoryMapper mapper = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        List<BinlogPhyDdlHistory> ddlHistories = mapper.select(
            s -> s
                .where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isEqualTo(tso))
                .and(BinlogPhyDdlHistoryDynamicSqlSupport.dbName, SqlBuilder.isEqualTo(dbName))
        );
        return ddlHistories.isEmpty() ? null : ddlHistories.get(0).getDdl();
    }

    @Override
    public void onStart(HandlerContext context) {
        context.getRuntimeContext().setServerId(serviceId);
        defaultCharset = context.getRuntimeContext().getDefaultDatabaseCharset();
        eventAcceptFilter.onStart(context);
    }

    @Override
    public void onStop() {
        eventAcceptFilter.onStop();
    }

    @Override
    public void onStartConsume(HandlerContext context) {
        delegate = (ITableMetaDelegate) context.getRuntimeContext()
            .getAttribute(RuntimeContext.ATTRIBUTE_TABLE_META_MANAGER);
        this.defaultCharset = context.getRuntimeContext().getDefaultDatabaseCharset();
        eventAcceptFilter.onStartConsume(context);
    }

}