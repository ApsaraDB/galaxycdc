// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: DumperServer.proto

package com.aliyun.polardbx.rpc.cdc;

public interface DumpRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:dumper.DumpRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string fileName = 1;</code>
   * @return The fileName.
   */
  java.lang.String getFileName();
  /**
   * <code>string fileName = 1;</code>
   * @return The bytes for fileName.
   */
  com.google.protobuf.ByteString
      getFileNameBytes();

  /**
   * <code>int64 position = 2;</code>
   * @return The position.
   */
  long getPosition();
}
