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

package com.aliyun.polardbx.binlog.collect.message;

import com.aliyun.polardbx.binlog.protocol.TxnToken;

/**
 *
 **/
public class MessageEvent {

    private TxnToken token;
    private boolean merged;

    public MessageEvent() {
    }

    public MessageEvent(TxnToken token) {
        this.token = token;
    }

    public TxnToken getToken() {
        return token;
    }

    public void setToken(TxnToken token) {
        this.token = token;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public void clear() {
        this.token = null;
        this.merged = false;
    }
}
