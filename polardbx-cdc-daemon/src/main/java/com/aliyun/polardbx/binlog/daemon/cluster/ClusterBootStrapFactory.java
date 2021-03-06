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

package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.daemon.cluster.service.BinlogBootstrapService;
import com.aliyun.polardbx.binlog.error.PolardbxException;

public class ClusterBootStrapFactory {
    public static ClusterBootstrapService getBootstrapService(ClusterTypeEnum clusterTypeEnum) {
        switch (clusterTypeEnum) {
        case BINLOG:
            return new BinlogBootstrapService();
        case IMPORT:
            throw new PolardbxException("not support this culster yet, " + clusterTypeEnum);
        case FLASHBACK:
            throw new PolardbxException("not support this culster yet, " + clusterTypeEnum);
        }
        return null;
    }
}
