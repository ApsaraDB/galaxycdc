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

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.MysqlEventParser;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.EventTransactionBuffer;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author shicai.xsc 2020/11/27 17:37
 * @since 5.0.0.0
 */
@Slf4j
public class NoTsoMysqlEventParser extends MysqlEventParser {

    // binLogParser
    protected LogEventConvert binlogParser;
    protected EventTransactionBuffer transactionBuffer;

    protected BinlogEventSink eventSink;
    protected boolean tryUseMysqlMinPosition;
    protected BinlogPosition startPosition;
    protected int MAX_RETRY = 20;
    protected boolean autoRetry = true;
    protected boolean directExitWhenStop = true;

    public NoTsoMysqlEventParser() {
        // ???????????????
        transactionBuffer = new EventTransactionBuffer(transaction -> {
            boolean succeeded = consumeTheEventAndProfilingIfNecessary(transaction);
            if (!running) {
                return;
            }

            if (!succeeded) {
                throw new CanalParseException("consume failed!");
            }
        });
    }

    protected boolean consumeTheEventAndProfilingIfNecessary(List<MySQLDBMSEvent> entrys) throws CanalParseException {
        // ???????????????,????????????
        //dumpTimeoutCount = 0;
        //dumpErrorCount = 0;

        long startTs = -1;
        boolean enabled = getProfilingEnabled();
        if (enabled) {
            startTs = System.currentTimeMillis();
        }

        boolean result = eventSink.sink(entrys);
        if (enabled) {
            this.processingInterval = System.currentTimeMillis() - startTs;
        }

        if (consumedEventCount.incrementAndGet() < 0) {
            consumedEventCount.set(0);
        }

        return result;
    }

    @Override
    public void start(final AuthenticationInfo master, final BinlogPosition position, final BinlogEventSink eventSink) {
        if (running) {
            return;
        }

        running = true;

        this.runningInfo = master;
        this.eventSink = eventSink;
        this.startPosition = position;

        // ??????transaction buffer
        // ?????????????????????
        transactionBuffer.setBufferSize(transactionSize);// ??????buffer??????
        transactionBuffer.start();

        // ??????????????????
        parseThread = new ParserThread();
        parseThread.setUncaughtExceptionHandler((t, e) -> {
            if (directExitWhenStop) {
                log.error("encounter uncaught exception, process will exit.", e);
                Runtime.getRuntime().halt(1);
            }
        });
        parseThread.setName(String.format("address = %s , EventParser",
            runningInfo == null ? null : runningInfo.getAddress().toString()));
        parseThread.start();
    }

    @Override
    protected BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) throws IOException {
        BinlogPosition startPosition = findStartPositionInternal(connection, position);
        if (needTransactionPosition.get()) {
            log.warn("prepare to find last position : " + startPosition.toString());
            Long preTransactionStartPosition = findTransactionBeginPosition(connection, startPosition);
            if (!preTransactionStartPosition.equals(startPosition.getPosition())) {
                log.warn("find new start Transaction Position {}, old : {}",
                    startPosition.getPosition(), preTransactionStartPosition);

                BinlogPosition newStartPosition = new BinlogPosition(startPosition.getFileName(),
                    preTransactionStartPosition,
                    startPosition.getMasterId(),
                    startPosition.getTimestamp());
                startPosition = newStartPosition;
            }
            needTransactionPosition.compareAndSet(true, false);
        }

        // ?????????????????????dbsync??????????????????,?????????????????????
        if (startPosition != null && startPosition.getFilePattern() == null) {
            connection.reconnect();
            BinlogPosition endPosition = findEndPosition((MysqlConnection) connection);
            startPosition.setFilePattern(endPosition.getFilePattern());
        }
        return startPosition;
    }

    @Override
    protected BinlogPosition findStartPositionInternal(ErosaConnection connection, BinlogPosition entryPosition) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        this.currentServerId = findServerId(mysqlConnection);
        if (entryPosition == null) {// ???????????????????????????
            return findEndPositionWithMasterIdAndTimestamp(mysqlConnection); // ?????????????????????????????????????????????
        }

        // binlog??????????????????,?????????????????????:
        // 1. binlog???????????????
        // 2. vip?????????mysql,?????????????????????,????????????serverId????????????,??????????????????????????????????????????????????????????????????binlog??????
        // ???????????????vip?????????????????????????????????????????????

        // ???????????????????????????????????????
        if (CommonUtil.isMeaninglessBinlogFileName(entryPosition)) {
            // ??????????????????binlogName???????????????timestamp????????????
            if (entryPosition.getTimestamp() > 0L) {
                log.warn("prepare to find start position ::" + entryPosition.getTimestamp());
                return findByStartTimeStamp(mysqlConnection, entryPosition.getTimestamp());
            } else {
                log.warn("prepare to find start position just show binlog events limit 1");
                return findStartPosition(mysqlConnection); // ?????????????????????????????????????????????
            }
        } else {
            if (entryPosition.getPosition() > 0L) {
                // ????????????binlogName + offest???????????????
                log.warn("prepare to find start position just last position " + entryPosition.getFileName() + ":"
                    + entryPosition.getPosition() + ":");
                return entryPosition;
                // return findPositionWithMasterIdAndTimestamp(mysqlConnection, entryPosition);
            } else {
                BinlogPosition specificLogFilePosition = null;
                if (entryPosition.getTimestamp() > 0L) {
                    // ????????????binlogName +
                    // timestamp???????????????????????????offest??????????????????????????????offest
                    BinlogPosition endPosition = findEndPosition(mysqlConnection);
                    if (endPosition != null) {
                        log.warn("prepare to find start position " + entryPosition.getFileName() + "::"
                            + entryPosition.getTimestamp());
                        specificLogFilePosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                            entryPosition.getTimestamp(),
                            endPosition,
                            entryPosition.getFileName());
                    }
                }

                if (specificLogFilePosition == null) {
                    // position??????????????????????????????
                    entryPosition = new BinlogPosition(entryPosition.getFileName(),
                        BINLOG_START_OFFEST,
                        entryPosition.getMasterId(),
                        entryPosition.getTimestamp());
                    return entryPosition;
                } else {
                    return specificLogFilePosition;
                }
            }
        }
    }

    protected BinlogPosition findPositionWithMasterIdAndTimestamp(MysqlConnection connection,
                                                                  BinlogPosition fixedPosition) {
        if (fixedPosition.getTimestamp() > 0) {
            return fixedPosition;
        }

        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        long startTimestamp = TimeUnit.MILLISECONDS
            .toSeconds(System.currentTimeMillis() + 102L * 365 * 24 * 3600 * 1000); // ?????????????????????102???
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            fixedPosition,
            fixedPosition.getFileName());
    }

    protected BinlogPosition findEndPositionWithMasterIdAndTimestamp(MysqlConnection connection) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        final BinlogPosition endPosition = findEndPosition(mysqlConnection);
        long startTimestamp = System.currentTimeMillis();
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            endPosition,
            endPosition.getFileName());
    }

    // ??????????????????binlog??????
    @Override
    protected BinlogPosition findByStartTimeStamp(MysqlConnection mysqlConnection, Long startTimestamp) {
        BinlogPosition endPosition = findEndPosition(mysqlConnection);
        BinlogPosition startPosition = findStartPosition(mysqlConnection);
        String maxBinlogFileName = endPosition.getFileName();
        String minBinlogFileName = startPosition.getFileName();
        log.info("show master status to set search end condition: " + endPosition);
        String startSearchBinlogFile = endPosition.getFileName();
        boolean shouldBreak = false;
        while (running && !shouldBreak) {
            try {
                BinlogPosition entryPosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                    startTimestamp,
                    endPosition,
                    startSearchBinlogFile);
                if (entryPosition == null) {
                    if (StringUtils.equalsIgnoreCase(minBinlogFileName, startSearchBinlogFile)) {
                        // ???????????????????????????binlog????????????????????????
                        shouldBreak = true;
                        log.warn("Didn't find the corresponding binlog files from " + minBinlogFileName + " to "
                            + maxBinlogFileName);
                    } else {
                        // ???????????????
                        int binlogSeqNum = Integer
                            .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                        if (binlogSeqNum <= 1) {
                            log.warn("Didn't find the corresponding binlog files");
                            shouldBreak = true;
                        } else {
                            int nextBinlogSeqNum = binlogSeqNum - 1;
                            String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                                startSearchBinlogFile.indexOf(".") + 1);
                            String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                            startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                        }
                    }
                } else {
                    log.info("found and return:" + endPosition + " in findByStartTimeStamp operation.");
                    return entryPosition;
                }
            } catch (Exception e) {
                log.warn("the binlogfile:" + startSearchBinlogFile
                        + " doesn't exist, to continue to search the next binlogfile , caused by ",
                    e);
                int binlogSeqNum = Integer
                    .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                if (binlogSeqNum <= 1) {
                    log.warn("Didn't find the corresponding binlog files");
                    shouldBreak = true;
                } else {
                    int nextBinlogSeqNum = binlogSeqNum - 1;
                    String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                        startSearchBinlogFile.indexOf(".") + 1);
                    String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                    startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                }
            }
        }
        // ?????????
        return null;
    }

    // ???????????????position???????????????position??????????????????rowdata??????????????????????????????????????????
    // ???????????????????????????????????????????????????????????????????????????timestamp??????????????????????????????????????????????????????
    protected Long findTransactionBeginPosition(ErosaConnection mysqlConnection,
                                              final BinlogPosition entryPosition) throws IOException {
        // ?????????????????????????????????
        final AtomicBoolean reDump = new AtomicBoolean(false);
        mysqlConnection.reconnect();
        binlogParser.refreshState();

        try {
            mysqlConnection.seek(entryPosition.getFileName(), entryPosition.getPosition(), new SinkFunction() {

                private BinlogPosition lastPosition;

                @Override
                public boolean sink(LogEvent event, LogPosition logPosition) {
                    try {
                        MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                        if (entry == null) {
                            return true;
                        }

                        DBMSEvent dbmsEvent = entry.getDbMessage();
                        // ?????????????????????????????????????????????????????????Begin/End
                        if (dbmsEvent instanceof DBMSTransactionBegin || dbmsEvent instanceof DBMSTransactionEnd ||
                            CommonUtil.isPolarDBXHeartbeat(dbmsEvent) || CommonUtil.isDDL(dbmsEvent)) {
                            lastPosition = buildLastPosition(entry);
                            return false;
                        } else {
                            reDump.set(true);
                            lastPosition = buildLastPosition(entry);
                            return false;
                        }
                    } catch (Exception e) {
                        // ??????????????????poistion???????????????update/insert/delete???????????????????????????dump??????????????????tableMap???????????????tableId???????????????
                        processSinkError(e, lastPosition, entryPosition.getFileName(), entryPosition.getPosition());
                        reDump.set(true);
                        return false;
                    }
                }
            });
        } catch (Exception e) {
            log.error("ERROR ## findTransactionBeginPosition has an error", e);
        }

        log.info("begin redump");

        // ??????????????????????????????Begin?????????????????????binlog??????
        if (reDump.get()) {
            final AtomicLong preTransactionStartPosition = new AtomicLong(0L);
            mysqlConnection.reconnect();
            try {
                mysqlConnection.seek(entryPosition.getFileName(), 4L, new SinkFunction() {

                    private BinlogPosition lastPosition;

                    @Override
                    public boolean sink(LogEvent event, LogPosition logPosition) {
                        try {

                            MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                            if (entry == null) {
                                return true;
                            }

                            DBMSEvent dbmsEvent = entry.getDbMessage();
                            // ?????????????????????????????????????????????????????????Begin
                            // ????????????transaction begin position
                            if ((dbmsEvent instanceof DBMSTransactionBegin ||
                                CommonUtil.isPolarDBXHeartbeat(dbmsEvent) || CommonUtil.isDDL(dbmsEvent))
                                && entry.getPosition().getPosition() < entryPosition.getPosition()) {
                                preTransactionStartPosition.set(entry.getPosition().getPosition());
                            }

                            if (entry.getPosition().getPosition() >= entryPosition.getPosition()) {
                                return false;// ??????
                            }

                            lastPosition = buildLastPosition(entry);
                        } catch (Exception e) {
                            processSinkError(e, lastPosition, entryPosition.getFileName(), entryPosition.getPosition());
                            return false;
                        }

                        return running;
                    }
                });
            } catch (Exception e) {
                log.error("ERROR ## findTransactionBeginPosition has an error", e);
            }

            // ??????????????????????????????position?????????????????????
            if (preTransactionStartPosition.get() > entryPosition.getPosition()) {
                log.error("preTransactionEndPosition greater than startPosition from zk or localconf, maybe lost data");
                throw new IOException(
                    "preTransactionStartPosition greater than startPosition from zk or localconf, maybe lost data");
            }
            return preTransactionStartPosition.get();
        } else {
            return entryPosition.getPosition();
        }
    }

    /**
     * ???????????????????????????????????????binlog?????????????????????????????????(????????????????????????)??????????????????????????????
     * ??????????????????binlog?????????endPosition????????????????????????
     */
    protected BinlogPosition findAsPerTimestampInSpecificLogFile(MysqlConnection mysqlConnection,
                                                               final Long startTimestamp,
                                                               final BinlogPosition endPosition,
                                                               final String searchBinlogFile) {

        final AtomicReference<BinlogPosition> ref = new AtomicReference<BinlogPosition>();
        try {
            mysqlConnection.reconnect();
            binlogParser.refreshState();
            // ?????????timestamp?????????????????????binlog parser???binlogfilename???"0"
            binlogParser.setBinlogFileName(searchBinlogFile);
            // ??????????????????
            mysqlConnection.seek(searchBinlogFile, 4L, new SinkFunction() {

                private BinlogPosition lastPosition;

                @Override
                public boolean sink(LogEvent event, LogPosition logPosition) {
                    BinlogPosition entryPosition = null;
                    try {
                        MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                        String logfilename = binlogParser.getBinlogFileName();
                        // String logfilename = searchBinlogFile;
                        Long logfileoffset = event.getLogPos();
                        Long logposTimestamp = event.getWhen();
                        Long masterId = event.getServerId();

                        // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                        if (logposTimestamp >= startTimestamp) {
                            return false;
                        }

                        // ??????????????? transaction ??? binlog
                        if (entry == null) {
                            if (StringUtils.equals(endPosition.getFileName(), searchBinlogFile)
                                || StringUtils.equals(endPosition.getFileName(), logfilename)) {
                                if (endPosition.getPosition() <= logfileoffset) {
                                    entryPosition = new BinlogPosition(logfilename,
                                        logfileoffset,
                                        masterId,
                                        logposTimestamp);
                                    if (log.isDebugEnabled()) {
                                        log.debug("set " + entryPosition
                                            + " to be pending start position before finding another proper one...");
                                    }
                                    /**
                                     * ????????? position ?????????????????????
                                     */
                                    ref.set(entryPosition);

                                    return false;
                                }
                            }

                            return true;
                        }

                        logfilename = entry.getPosition().getFileName();

                        DBMSEvent dbmsEvent = entry.getDbMessage();
                        if (dbmsEvent instanceof DBMSTransactionBegin || dbmsEvent instanceof DBMSTransactionEnd) {
                            if (log.isDebugEnabled()) {
                                log.debug(String.format("compare exit condition:%s,%s,%s, startTimestamp=%s...",
                                    logfilename,
                                    String.valueOf(logfileoffset),
                                    String.valueOf(logposTimestamp),
                                    String.valueOf(startTimestamp)));
                            }
                        }

                        if (StringUtils.equals(endPosition.getFileName(), searchBinlogFile)
                            || StringUtils.equals(endPosition.getFileName(), logfilename)) {
                            if (endPosition.getPosition() <= logfileoffset) {
                                return false;
                            }
                        }

                        // ??????????????????????????????????????????????????????????????????position
                        // position = current +
                        // data.length??????????????????????????????offest??????????????????????????????
                        if (dbmsEvent instanceof DBMSTransactionEnd || dbmsEvent instanceof DBMSTransactionBegin) {
                            entryPosition = new BinlogPosition(logfilename, logfileoffset, masterId, logposTimestamp);
                            if (log.isDebugEnabled()) {
                                log.debug("set " + entryPosition
                                    + " to be pending start position before finding another proper one...");
                            }
                            ref.set(entryPosition);
                        }

                        lastPosition = buildLastPosition(entry);
                    } catch (Throwable e) {
                        processSinkError(e, lastPosition, searchBinlogFile, 4L);
                    }

                    return running;
                }
            });
        } catch (Exception e) {
            log.error("ERROR ## findAsPerTimestampInSpecificLogFile has an error", e);
        }

        if (ref.get() != null) {
            return ref.get();
        } else {
            return null;
        }
    }

    /**
     * @param isSeek ????????????
     */
    protected MySQLDBMSEvent parseAndProfilingIfNecessary(LogEvent bod, boolean isSeek) throws Exception {
        long startTs = -1;
        boolean enabled = getProfilingEnabled();
        if (enabled) {
            startTs = System.currentTimeMillis();
        }
        MySQLDBMSEvent event = binlogParser.parse(bod, isSeek);
        if (enabled) {
            this.parsingInterval = System.currentTimeMillis() - startTs;
        }

        if (parsedEventCount.incrementAndGet() < 0) {
            parsedEventCount.set(0);
        }
        return event;
    }

    protected BinlogPosition buildLastPosition(MySQLDBMSEvent entry) { // ???????????????
        return entry.getPosition();
    }

    private class ParserThread extends Thread {
        int retry = 0;

        @Override
        public void run() {
            ErosaConnection erosaConnection = null;
            while (running) {
                try {
                    // ????????????replication
                    // 1. ??????Erosa??????
                    erosaConnection = buildErosaConnection();

                    // 2. ????????????????????????
                    // startHeartBeat(erosaConnection);

                    // 3. ??????dump???????????????????????????????????????metaConnection
                    preDump(erosaConnection);

                    erosaConnection.connect();

                    // 5. ???????????????????????????
                    BinlogPosition processedStartPosition = findStartPosition(erosaConnection, startPosition);
                    if (processedStartPosition == null) {
                        if (tryUseMysqlMinPosition) {
                            processedStartPosition = findStartPosition((MysqlConnection) erosaConnection);
                            log.warn("can't find start position, will use mysql min position start!:"
                                + processedStartPosition);
                        } else {
                            throw new PositionNotFoundException("can't find start position");
                        }
                    }
                    startPosition = processedStartPosition;

                    if (!processTableMeta(startPosition)) {
                        throw new CanalParseException(
                            "can't find init table meta for with position : " + startPosition);
                    }
                    log.warn("find start position : " + startPosition.toString());
                    // ???????????????????????????position????????????????????????????????????????????????
                    erosaConnection.reconnect();
                    binlogParser.refreshState();
                    binlogParser.setBinlogFileName(startPosition.getFileName());


                    final SinkFunction sinkHandler = new SinkFunction() {

                        private BinlogPosition lastPosition;

                        @Override
                        public boolean sink(LogEvent event, LogPosition logPosition) throws CanalParseException,
                            TableIdNotFoundException {
                            try {
                                MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, false);

                                if (!running) {
                                    return false;
                                }

                                if (entry != null) {
                                    transactionBuffer.add(entry);
                                    // ?????????????????????positions
                                    this.lastPosition = buildLastPosition(entry);
                                }
                                StatisticalProxy.getInstance().heartbeat();
                                return running;
                            } catch (TableIdNotFoundException e) {
                                throw e;
                            } catch (Throwable e) {
                                if (e.getCause() instanceof TableIdNotFoundException) {
                                    throw (TableIdNotFoundException) e.getCause();
                                }
                                // ????????????????????????????????????
                                processSinkError(e,
                                    this.lastPosition,
                                    startPosition.getFileName(),
                                    startPosition.getPosition());
                                throw new CanalParseException(e); // ??????????????????????????????????????????
                            }
                        }
                    };

                    // 4. ??????dump??????
                    erosaConnection.dump(startPosition.getFileName(),
                        startPosition.getPosition(),
                        startPosition.getTimestamp(),
                        sinkHandler);
                } catch (TableIdNotFoundException e) {
                    // ????????????TableIdNotFound??????,???????????????????????????????????????????????????position??????????????????????????????tablemap
                    // Event??????????????????
                    needTransactionPosition.compareAndSet(false, true);
                    log.error(String.format("dump address %s has an error, retrying. caused by ",
                        runningInfo.getAddress().toString()), e);
                } catch (Throwable e) {
                    processDumpError(e);
                    if (!running) {
                        if (!(e instanceof java.nio.channels.ClosedByInterruptException
                            || e.getCause() instanceof java.nio.channels.ClosedByInterruptException)) {
                            throw new CanalParseException(String.format("dump address %s has an error, retrying. ",
                                runningInfo.getAddress().toString()), e);
                        }
                    } else {
                        log.error(String.format("dump address %s has an error, retrying. caused by ",
                            runningInfo.getAddress().toString()), e);
                    }
                } finally {
                    // ????????????????????????
                    Thread.interrupted();
                    // ??????????????????
                    afterDump(erosaConnection);
                    try {
                        if (erosaConnection != null) {
                            erosaConnection.disconnect();
                        }
                    } catch (IOException e1) {
                        log.error("disconnect address " + runningInfo.getAddress().toString()
                                + " has an error, retrying., caused by ",
                            e1);
                    }
                }
                // ?????????????????????sink???????????????????????????
                transactionBuffer.reset();// ?????????????????????????????????????????????
                binlogParser.reset();// ????????????

                log.error("ParserThread run failed, retry: {}", retry);
                if (++retry >= MAX_RETRY || !autoRetry) {
                    running = false;
                }
                if (running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (directExitWhenStop) {
                log.error("ParserThread failed after retry: {}, process exit", retry);
                System.exit(1);
            }
        }
    }

    public void setBinlogParser(LogEventConvert binlogParser) {
        this.binlogParser = binlogParser;
    }

    public void setAutoRetry(boolean autoRetry) {
        this.autoRetry = autoRetry;
    }

    public void setDirectExitWhenStop(boolean directExitWhenStop) {
        this.directExitWhenStop = directExitWhenStop;
    }
}
