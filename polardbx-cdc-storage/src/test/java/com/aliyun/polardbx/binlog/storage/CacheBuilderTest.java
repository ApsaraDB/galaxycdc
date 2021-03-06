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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 **/
public class CacheBuilderTest {

    public static void main(String args[]) {
        singleThreadTest();
    }

    private static void multiThreadTest() {
        LoadingCache<TxnKey, TxnBuffer> txnCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<TxnKey, TxnBuffer>() {

                @Override
                public TxnBuffer load(TxnKey key) throws Exception {
                    return new TxnBuffer(key, null);
                }
            });

        Thread[] threads = new Thread[4];

        int count = 1000000;
        List<TxnKey> txnKeys = new ArrayList<>();
        long seed = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            txnKeys.add(new TxnKey(String.valueOf(seed++), "111"));
        }
        long start = System.currentTimeMillis();
        txnKeys.forEach(k -> txnCache.getUnchecked(k));

        long end = System.currentTimeMillis();
        System.out.println("cost time is:" + (end - start));
        System.out.println("tps is:" + ((double) count / (end - start)));
        System.out.println("cache size:" + txnCache.size());
    }

    private static void singleThreadTest() {
        LoadingCache<TxnKey, TxnBuffer> txnCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<TxnKey, TxnBuffer>() {

                @Override
                public TxnBuffer load(TxnKey key) throws Exception {
                    return new TxnBuffer(key, null);
                }
            });

        int count = 4000000;
        List<TxnKey> txnKeys = new ArrayList<>();
        long seed = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            txnKeys.add(new TxnKey(String.valueOf(seed++), "111"));
        }

        HashMap<TxnKey, TxnBuffer> map = new HashMap<>();
        txnKeys.forEach(k -> txnCache.getUnchecked(k));
        long start = System.currentTimeMillis();
        txnKeys.forEach(k -> txnCache.invalidate(k));
        long end = System.currentTimeMillis();
        System.out.println("cost time is:" + (end - start));
        System.out.println("tps is:" + ((double) count / (end - start)));
        System.out.println("cache size:" + txnCache.size());
    }
}
