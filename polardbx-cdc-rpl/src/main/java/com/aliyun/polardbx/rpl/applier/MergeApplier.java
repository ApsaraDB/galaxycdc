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

package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/5/17 20:52
 * @since 5.0.0.0
 */
@Slf4j
public class MergeApplier extends MergeTransactionApplier {

    ExecutorService mergeAndTranExecutorService;
    MergeTransactionApplier mergeTransactionApplier;

    public MergeApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    public boolean init() {
        super.init();
        mergeTransactionApplier = new MergeTransactionApplier(applierConfig, hostInfo);
        mergeTransactionApplier.init();
        mergeTransactionApplier.dbMetaCache = dbMetaCache;
        mergeAndTranExecutorService = ThreadPoolUtil.createExecutorWithFixedNum(2, "MergeApplier");
        return true;
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }

        // Map<fullTableName, Map<rowPk/rowUk, RowChange>>
        Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges = new HashMap<>();
        Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges = new HashMap<>();
        Map<String, DefaultRowChange> lastRowChanges = new HashMap<>();
        Set<String> changedIdentifyColumnTables = new HashSet<>();
        mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges, lastRowChanges, changedIdentifyColumnTables);

        // ?????????????????????
        // ?????? a.a????????? uk ?????? pk: create table a.a (f0 int, f1 int, f2 int, primary key(f0), unique key uk(f1));
        // ?????????????????????????????????????????????

        // ????????????????????? A
        // A.1 insert (1, 1, 1)?????????: (1, 1, 1)
        // A.2 insert (2, 2, 2)?????????: (1, 1, 1), (2, 2, 2)
        // A.3 update (1, 1, 1) to (1, 3, 1), ??????: (1, 3, 1), (2, 2, 2)
        // A.4 update (2, 2, 2) to (2, 1, 2), ??????: (1, 3, 1), (2, 1, 2)
        // ???????????????????????? B

        // ????????????????????? A ??????????????????????????????????????????????????? A.1 ????????? A-B ?????????????????????
        // ???????????? sql: insert (1, 1, 1) on duplicate key update???
        // ?????? sql ????????????????????????????????? uk(f1) ?????????

        // ?????????????????? pk/uk/shard key ????????? events?????????????????? events ??????????????????????????????????????? B???
        // ??????????????????????????????????????? A-B ??????????????????????????????

        // ??? MergeApplier ????????????????????? events ???????????? applierConfig.mergeBatchSize ??????????????????????????????
        // ???????????? A-B ????????? events ??????????????????????????????????????????????????????
        // ??????????????????????????????????????????????????? events ????????? B???
        // ????????????????????????????????? pk/uk/shard key ????????????????????????????????????????????????????????????????????? MergeTransactionApplier???
        // MergeTransactionApplier ???????????????????????? A-B ????????? events???????????????????????????????????? B???
        Map<String, Map<RowKey, DefaultRowChange>> tranInsertRowChanges = new HashMap<>();
        Map<String, Map<RowKey, DefaultRowChange>> tranDeleteRowChanges = new HashMap<>();
        for (String fullTbName : changedIdentifyColumnTables) {
            tranInsertRowChanges.put(fullTbName, insertRowChanges.get(fullTbName));
            tranDeleteRowChanges.put(fullTbName, deleteRowChanges.get(fullTbName));
            insertRowChanges.remove(fullTbName);
            deleteRowChanges.remove(fullTbName);
            log.info("{} changes will be executed by MergeTransactionApplier", fullTbName);
        }

        if (tranInsertRowChanges.size() == 0 && tranDeleteRowChanges.size() == 0) {
            return parallelExecSqlContexts(deleteRowChanges, DBMSAction.DELETE)
                && parallelExecSqlContexts(insertRowChanges, DBMSAction.INSERT);
        }

        // mergeTransactionApplier ??? mergeApplier ??????
        List<Future> futures = new ArrayList<>();
        Callable tranTask = () -> {
            try {
                return mergeTransactionApplier
                    .parallelExecSqlContexts(tranInsertRowChanges, tranDeleteRowChanges, lastRowChanges);
            } catch (Throwable e) {
                log.error("mergeTransactionApplier parallelExecSqlContexts failed: " + e);
                return false;
            }
        };
        Callable task = () -> {
            try {
                return parallelExecSqlContexts(deleteRowChanges, DBMSAction.DELETE)
                    && parallelExecSqlContexts(insertRowChanges, DBMSAction.INSERT);
            } catch (Throwable e) {
                log.error("mergeApplier parallelExecSqlContexts failed: " + e);
                return false;
            }
        };
        futures.add(mergeAndTranExecutorService.submit(() -> tranTask.call()));
        futures.add(mergeAndTranExecutorService.submit(() -> task.call()));

        // get result
        boolean res = true;
        for (Future future : futures) {
            res &= (Boolean) future.get();
        }

        return res;
    }

    private boolean parallelExecSqlContexts(Map<String, Map<RowKey, DefaultRowChange>> allRowChanges,
                                            DBMSAction action) throws Throwable {
        List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

        for (String tbName : allRowChanges.keySet()) {
            Collection<DefaultRowChange> tbRowChanges = allRowChanges.get(tbName).values();
            if (tbRowChanges.size() == 0) {
                continue;
            }

            // merge
            List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(tbRowChanges,
                RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
            mergeDmlSqlContexts.addAll(sqlContexts);
        }

        boolean res = true;
        List<Future> futures = new ArrayList<>();

        // parallel execute, each table cost a thread
        for (MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            Callable task = () -> {
                boolean succeed = execSqlContexts(Arrays.asList(sqlContext));
                sqlContext.setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(() -> task.call()));
            // record merge size
            StatisticalProxy.getInstance().addMergeBatchSize(sqlContext.getOriginRowChanges().size());
        }

        // get result
        for (Future future : futures) {
            res &= (Boolean) future.get();
        }

        // return res;

        if (res) {
            return res;
        }

        // for those failed sqlContext, excute the originRowChanges with the sql to be
        // INSERT ON DUPLICATE UPDATE
        futures.clear();
        res = true;
        for (final MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            if (sqlContext.isSucceed()) {
                continue;
            }

            log.error("merge execute failed for: {}, action: {}, try serial execute",
                sqlContext.getDstTable(),
                action.name());

            for (DefaultRowChange rowChange : sqlContext.getOriginRowChanges()) {
                final Callable task = () -> {
                    List<SqlContext> newSqlContexts = getSqlContexts(rowChange, sqlContext.getDstTable());
                    // execute
                    return newSqlContexts == null ? false : execSqlContexts(newSqlContexts);
                };
                futures.add(executorService.submit(() -> task.call()));
            }
        }

        for (Future future : futures) {
            res &= (Boolean) future.get();
        }

        if (!res) {
            log.error("single execute failed");
        }

        return res;
    }
}
