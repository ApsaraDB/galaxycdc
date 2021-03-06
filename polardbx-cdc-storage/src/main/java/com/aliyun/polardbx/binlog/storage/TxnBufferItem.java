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

package com.aliyun.polardbx.binlog.storage;

import com.google.protobuf.ByteString;
import lombok.Builder;
import lombok.Data;

/**
 * Created by ziyang.lb
 **/
@Data
@Builder
public class TxnBufferItem {
    private String traceId;
    private String rowsQuery;
    private int eventType;
    private byte[] payload;
    private ByteString byteStringPayload;
    private String schema;
    private String table;

    //可选项
    private String binlogFile;
    private long binlogPosition;
    private String originTraceId;

    public int size() {
        if (payload != null) {
            return payload.length;
        }
        if (byteStringPayload != null) {
            return byteStringPayload.size();
        }

        throw new IllegalStateException("Both payload and byteStringPayload is null");
    }
}
