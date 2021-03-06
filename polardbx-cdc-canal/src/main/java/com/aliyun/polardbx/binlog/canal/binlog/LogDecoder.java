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

package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.IBinlogFileSizeFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.event.AppendBlockLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.BeginLoadQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.CreateFileLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteFileLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.ExecuteLoadLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.ExecuteLoadQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GtidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.HeartbeatLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IgnorableLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IncidentLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LoadLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.PreviousGtidsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.StartLogEventV3;
import com.aliyun.polardbx.binlog.canal.binlog.event.StopLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TransactionContextLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.BinlogCheckPointLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.MariaGtidListLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.MariaGtidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.StartEncryptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.BitSet;

/**
 * Implements a binary-log decoder.
 *
 * <pre>
 * LogDecoder decoder = new LogDecoder();
 * decoder.handle(...);
 *
 * LogEvent event;
 * do
 * {
 *     event = decoder.decode(buffer, context);
 *
 *     // process log event.
 * }
 * while (event != null);
 * // no more events in buffer.
 * </pre>
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class LogDecoder {

    protected static final Logger logger = LoggerFactory.getLogger(LogDecoder.class);

    protected final BitSet handleSet = new BitSet(LogEvent.ENUM_END_EVENT);

    protected IBinlogFileSizeFetcher binlogFileSizeFetcher;

    public LogDecoder() {
    }

    public LogDecoder(final int fromIndex, final int toIndex) {
        handleSet.set(fromIndex, toIndex);
    }

    /**
     * Deserialize an event from buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped.
     */
    public LogEvent decode(LogBuffer buffer, LogHeader header, LogContext context) throws IOException {
        FormatDescriptionLogEvent descriptionEvent = context.getFormatDescription();
        LogPosition logPosition = context.getLogPosition();

        int checksumAlg = LogEvent.BINLOG_CHECKSUM_ALG_UNDEF;
        if (header.getType() != LogEvent.FORMAT_DESCRIPTION_EVENT) {
            checksumAlg = descriptionEvent.header.getChecksumAlg();
        } else {
            // ?????????format??????????????????????????????checksum
            checksumAlg = header.getChecksumAlg();
        }

        if (checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_OFF && checksumAlg != LogEvent.BINLOG_CHECKSUM_ALG_UNDEF) {
            // remove checksum bytes
            buffer.limit(header.getEventLen() - LogEvent.BINLOG_CHECKSUM_LEN);
        }

        switch (header.getType()) {
        case LogEvent.SEQUENCE_EVENT: {
            SequenceLogEvent event = new SequenceLogEvent(header, buffer, descriptionEvent);
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GCN_EVENT: {
            GcnLogEvent event = new GcnLogEvent(header, buffer, descriptionEvent);
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.QUERY_EVENT: {
            QueryLogEvent event = new QueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.XID_EVENT: {
            XidLogEvent event = new XidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.TABLE_MAP_EVENT: {
            ServerCharactorSet charactorSet = context.getServerCharactorSet();
            TableMapLogEvent mapEvent = new TableMapLogEvent(header, buffer, descriptionEvent,
                CharsetConversion.getJavaCharset(charactorSet.getCharacterSetServer()));
            /* updating position in context */
            logPosition.position = header.getLogPos();
            context.putTable(mapEvent);
            return mapEvent;
        }
        case LogEvent.WRITE_ROWS_EVENT_V1: {
            RowsLogEvent event = new WriteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.UPDATE_ROWS_EVENT_V1: {
            RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.DELETE_ROWS_EVENT_V1: {
            RowsLogEvent event = new DeleteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.ROTATE_EVENT: {
            RotateLogEvent event = new RotateLogEvent(header, buffer, descriptionEvent);
            event = tryFixRotateEvent(event, logPosition);
            /* updating position in context */
            logPosition = new LogPosition(event.getFilename(), event.getPosition());
            context.setLogPosition(logPosition);
            return event;
        }
        case LogEvent.LOAD_EVENT:
        case LogEvent.NEW_LOAD_EVENT: {
            LoadLogEvent event = new LoadLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.SLAVE_EVENT: /* can never happen (unused event) */ {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported SLAVE_EVENT from: "
                    + context.getLogPosition());
            }
            break;
        }
        case LogEvent.CREATE_FILE_EVENT: {
            CreateFileLogEvent event = new CreateFileLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.APPEND_BLOCK_EVENT: {
            AppendBlockLogEvent event = new AppendBlockLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.DELETE_FILE_EVENT: {
            DeleteFileLogEvent event = new DeleteFileLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.EXEC_LOAD_EVENT: {
            ExecuteLoadLogEvent event = new ExecuteLoadLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.START_EVENT_V3: {
            /* This is sent only by MySQL <=4.x */
            StartLogEventV3 event = new StartLogEventV3(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.STOP_EVENT: {
            StopLogEvent event = new StopLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.INTVAR_EVENT: {
            IntvarLogEvent event = new IntvarLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.RAND_EVENT: {
            RandLogEvent event = new RandLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.USER_VAR_EVENT: {
            UserVarLogEvent event = new UserVarLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.FORMAT_DESCRIPTION_EVENT: {
            descriptionEvent = new FormatDescriptionLogEvent(header, buffer, descriptionEvent);
            context.setFormatDescription(descriptionEvent);
            return descriptionEvent;
        }
        case LogEvent.PRE_GA_WRITE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_WRITE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Write_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.PRE_GA_UPDATE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_UPDATE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Update_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.PRE_GA_DELETE_ROWS_EVENT: {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping unsupported PRE_GA_DELETE_ROWS_EVENT from: "
                    + context.getLogPosition());
            }
            // ev = new Delete_rows_log_event_old(buf, event_len,
            // description_event);
            break;
        }
        case LogEvent.BEGIN_LOAD_QUERY_EVENT: {
            BeginLoadQueryLogEvent event = new BeginLoadQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.EXECUTE_LOAD_QUERY_EVENT: {
            ExecuteLoadQueryLogEvent event = new ExecuteLoadQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.INCIDENT_EVENT: {
            IncidentLogEvent event = new IncidentLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.HEARTBEAT_LOG_EVENT: {
            HeartbeatLogEvent event = new HeartbeatLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.IGNORABLE_LOG_EVENT: {
            IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.ROWS_QUERY_LOG_EVENT: {
            RowsQueryLogEvent event = new RowsQueryLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.WRITE_ROWS_EVENT: {
            RowsLogEvent event = new WriteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.UPDATE_ROWS_EVENT: {
            RowsLogEvent event = new UpdateRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.DELETE_ROWS_EVENT: {
            RowsLogEvent event = new DeleteRowsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            event.fillTable(context);
            return event;
        }
        case LogEvent.GTID_LOG_EVENT:
        case LogEvent.ANONYMOUS_GTID_LOG_EVENT: {
            GtidLogEvent event = new GtidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.PREVIOUS_GTIDS_LOG_EVENT: {
            PreviousGtidsLogEvent event = new PreviousGtidsLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.TRANSACTION_CONTEXT_EVENT: {
            TransactionContextLogEvent event = new TransactionContextLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        // case LogEvent.VIEW_CHANGE_EVENT: {
        // ViewChangeEvent event = new ViewChangeEvent(header, buffer,
        // descriptionEvent);
        // /* updating position in context */
        // logPosition.position = header.getLogPos();
        // return event;
        // }
        case LogEvent.XA_PREPARE_LOG_EVENT: {
            XaPrepareLogEvent event = new XaPrepareLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.ANNOTATE_ROWS_EVENT: {
            AnnotateRowsEvent event = new AnnotateRowsEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.BINLOG_CHECKPOINT_EVENT: {
            BinlogCheckPointLogEvent event = new BinlogCheckPointLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GTID_EVENT: {
            MariaGtidLogEvent event = new MariaGtidLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.GTID_LIST_EVENT: {
            MariaGtidListLogEvent event = new MariaGtidListLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }
        case LogEvent.START_ENCRYPTION_EVENT: {
            StartEncryptionLogEvent event = new StartEncryptionLogEvent(header, buffer, descriptionEvent);
            /* updating position in context */
            logPosition.position = header.getLogPos();
            return event;
        }

        default:
            /*
             * Create an object of Ignorable_log_event for unrecognized sub-class. So that SLAVE SQL THREAD will
             * only update the position and continue.
             */
            if ((buffer.getUint16(LogEvent.FLAGS_OFFSET) & LogEvent.LOG_EVENT_IGNORABLE_F) > 0) {
                IgnorableLogEvent event = new IgnorableLogEvent(header, buffer, descriptionEvent);
                /* updating position in context */
                logPosition.position = header.getLogPos();
                return event;
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("Skipping unrecognized binlog event " + LogEvent.getTypeName(header.getType())
                        + " from: " + context.getLogPosition());
                }
            }
        }

        /* updating position in context */
        logPosition.position = header.getLogPos();
        /* Unknown or unsupported log event */
        return new UnknownLogEvent(header);
    }

    public final void handle(final int fromIndex, final int toIndex) {
        handleSet.set(fromIndex, toIndex);
    }

    public final void handle(final int flagIndex) {
        handleSet.set(flagIndex);
    }

    /**
     * Decoding an event from binary-log buffer.
     *
     * @return <code>UknownLogEvent</code> if event type is unknown or skipped,
     * <code>null</code> if buffer is not including a full event.
     */
    public LogEvent decode(LogBuffer buffer, LogContext context) throws IOException {
        final int limit = buffer.limit();

        if (limit >= FormatDescriptionLogEvent.LOG_EVENT_HEADER_LEN) {
            LogHeader header = new LogHeader(buffer, context.getFormatDescription());

            final int len = header.getEventLen();
            if (limit >= len) {
                LogEvent event;
                header.processCheckSum(buffer);
                header.initDataBuffer(buffer);
                /* Checking binary-log's header */
                if (handleSet.get(header.getType())) {
                    buffer.limit(len);
                    try {
                        /* Decoding binary-log to event */
                        event = decode(buffer, header, context);
                    } catch (IOException e) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Decoding " + LogEvent.getTypeName(header.getType()) + " failed from: "
                                    + context.getLogPosition(),
                                e);
                        }
                        throw e;
                    } finally {
                        /* Restore limit */
                        buffer.limit(limit);
                    }
                } else {
                    /* Ignore unsupported binary-log. */
                    event = new UnknownLogEvent(header);
                }

                /* consume this binary-log. */
                buffer.consume(len);
                return event;
            }
        }

        /* Rewind buffer's position to 0. */
        buffer.rewind();
        return null;
    }

    private RotateLogEvent tryFixRotateEvent(RotateLogEvent event, LogPosition logPosition) throws IOException {
        if (binlogFileSizeFetcher != null && logPosition != null && StringUtils.isNotBlank(logPosition.getFileName())
            && !StringUtils.equals(logPosition.getFileName(), event.getFilename())) {
             /*
             xdb??????bug???????????????binlog??????????????????????????????RotateEvent??????????????????????????????????????????rotate event????????????????????????
             ?????????rotate event????????????logPosition??????????????????????????????????????????logPosition.getPosition() + length(rotateEvent)??????
             ??????binlog?????????size????????????rotate event????????????binlog???????????????????????????????????????logPosition??????position???????????????long??????
             ??????binlog?????????LogHeader??????????????????????????????uint32(???????????????4G)??????logPosition???position??????????????????????????????uint32???
             ????????????????????????(???20G????????????)???logPosition.getPosition() + length(rotateEvent)?????????????????????binlog?????????size???????????????
             ???????????????????????????????????????rotate event????????????binlog????????????????????????xdb???????????????max_binlog_size???????????????????????????1073741824(1G)???
             ????????????????????????????????????????????????binlog?????????????????????max_binlog_size?????????rotate event?????????binlog??????????????????????????????????????????
             max_binlog_size??????????????????????????????????????????logPosition???position??????????????????uint32?????????????????????rotate event??????????????????rotate event
              */
            long fileSize = binlogFileSizeFetcher.fetch(logPosition.getFileName());
            long length = logPosition.getPosition() + event.getEventLen();
            if (length < fileSize && length < Integer.MAX_VALUE) {
                RotateLogEvent newEvent = new RotateLogEvent(event.getHeader(), logPosition.getFileName(), length);
                logger.warn("receive a invalid rotate event, will fix it, the event info before fix is :" + event.info()
                    + " ,the event info after fix is :" + newEvent.info());
                return newEvent;
            }
        }
        return event;
    }

    public void setBinlogFileSizeFetcher(IBinlogFileSizeFetcher binlogFileSizeFetcher) {
        this.binlogFileSizeFetcher = binlogFileSizeFetcher;
    }
}
