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

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TxnStream.proto

package com.aliyun.polardbx.binlog.protocol;

/**
 * Protobuf type {@code com.aliyun.polardbx.binlog.protocol.TxnMergedToken}
 */
public final class TxnMergedToken extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:com.aliyun.polardbx.binlog.protocol.TxnMergedToken)
    TxnMergedTokenOrBuilder {
    private static final long serialVersionUID = 0L;

    // Use TxnMergedToken.newBuilder() to construct.
    private TxnMergedToken(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
        super(builder);
    }

    private TxnMergedToken() {
        tso_ = "";
        type_ = 0;
        beginSchema_ = "";
        payload_ = com.google.protobuf.ByteString.EMPTY;
    }

    @java.lang.Override
    @SuppressWarnings({"unused"})
    protected java.lang.Object newInstance(
        UnusedPrivateParameter unused) {
        return new TxnMergedToken();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
        return this.unknownFields;
    }

    private TxnMergedToken(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        this();
        if (extensionRegistry == null) {
            throw new java.lang.NullPointerException();
        }
        com.google.protobuf.UnknownFieldSet.Builder unknownFields =
            com.google.protobuf.UnknownFieldSet.newBuilder();
        try {
            boolean done = false;
            while (!done) {
                int tag = input.readTag();
                switch (tag) {
                case 0:
                    done = true;
                    break;
                case 10: {
                    java.lang.String s = input.readStringRequireUtf8();

                    tso_ = s;
                    break;
                }
                case 16: {
                    int rawValue = input.readEnum();

                    type_ = rawValue;
                    break;
                }
                case 26: {
                    java.lang.String s = input.readStringRequireUtf8();

                    beginSchema_ = s;
                    break;
                }
                case 34: {

                    payload_ = input.readBytes();
                    break;
                }
                default: {
                    if (!parseUnknownField(
                        input, unknownFields, extensionRegistry, tag)) {
                        done = true;
                    }
                    break;
                }
                }
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw e.setUnfinishedMessage(this);
        } catch (java.io.IOException e) {
            throw new com.google.protobuf.InvalidProtocolBufferException(
                e).setUnfinishedMessage(this);
        } finally {
            this.unknownFields = unknownFields.build();
            makeExtensionsImmutable();
        }
    }

    public static final com.google.protobuf.Descriptors.Descriptor
    getDescriptor() {
        return com.aliyun.polardbx.binlog.protocol.TxnStream.internal_static_com_aliyun_polardbx_binlog_protocol_TxnMergedToken_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
    internalGetFieldAccessorTable() {
        return com.aliyun.polardbx.binlog.protocol.TxnStream.internal_static_com_aliyun_polardbx_binlog_protocol_TxnMergedToken_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                com.aliyun.polardbx.binlog.protocol.TxnMergedToken.class,
                com.aliyun.polardbx.binlog.protocol.TxnMergedToken.Builder.class);
    }

    public static final int TSO_FIELD_NUMBER = 1;
    private volatile java.lang.Object tso_;

    /**
     * <code>string tso = 1;</code>
     *
     * @return The tso.
     */
    @java.lang.Override
    public java.lang.String getTso() {
        java.lang.Object ref = tso_;
        if (ref instanceof java.lang.String) {
            return (java.lang.String) ref;
        } else {
            com.google.protobuf.ByteString bs =
                (com.google.protobuf.ByteString) ref;
            java.lang.String s = bs.toStringUtf8();
            tso_ = s;
            return s;
        }
    }

    /**
     * <code>string tso = 1;</code>
     *
     * @return The bytes for tso.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
    getTsoBytes() {
        java.lang.Object ref = tso_;
        if (ref instanceof java.lang.String) {
            com.google.protobuf.ByteString b =
                com.google.protobuf.ByteString.copyFromUtf8(
                    (java.lang.String) ref);
            tso_ = b;
            return b;
        } else {
            return (com.google.protobuf.ByteString) ref;
        }
    }

    public static final int TYPE_FIELD_NUMBER = 2;
    private int type_;

    /**
     * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
     *
     * @return The enum numeric value on the wire for type.
     */
    @java.lang.Override
    public int getTypeValue() {
        return type_;
    }

    /**
     * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
     *
     * @return The type.
     */
    @java.lang.Override
    public com.aliyun.polardbx.binlog.protocol.TxnType getType() {
        @SuppressWarnings("deprecation")
        com.aliyun.polardbx.binlog.protocol.TxnType result = com.aliyun.polardbx.binlog.protocol.TxnType.valueOf(type_);
        return result == null ? com.aliyun.polardbx.binlog.protocol.TxnType.UNRECOGNIZED : result;
    }

    public static final int BEGINSCHEMA_FIELD_NUMBER = 3;
    private volatile java.lang.Object beginSchema_;

    /**
     * <code>string beginSchema = 3;</code>
     *
     * @return The beginSchema.
     */
    @java.lang.Override
    public java.lang.String getBeginSchema() {
        java.lang.Object ref = beginSchema_;
        if (ref instanceof java.lang.String) {
            return (java.lang.String) ref;
        } else {
            com.google.protobuf.ByteString bs =
                (com.google.protobuf.ByteString) ref;
            java.lang.String s = bs.toStringUtf8();
            beginSchema_ = s;
            return s;
        }
    }

    /**
     * <code>string beginSchema = 3;</code>
     *
     * @return The bytes for beginSchema.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString
    getBeginSchemaBytes() {
        java.lang.Object ref = beginSchema_;
        if (ref instanceof java.lang.String) {
            com.google.protobuf.ByteString b =
                com.google.protobuf.ByteString.copyFromUtf8(
                    (java.lang.String) ref);
            beginSchema_ = b;
            return b;
        } else {
            return (com.google.protobuf.ByteString) ref;
        }
    }

    public static final int PAYLOAD_FIELD_NUMBER = 4;
    private com.google.protobuf.ByteString payload_;

    /**
     * <code>bytes payload = 4;</code>
     *
     * @return The payload.
     */
    @java.lang.Override
    public com.google.protobuf.ByteString getPayload() {
        return payload_;
    }

    private byte memoizedIsInitialized = -1;

    @java.lang.Override
    public final boolean isInitialized() {
        byte isInitialized = memoizedIsInitialized;
        if (isInitialized == 1) {
            return true;
        }
        if (isInitialized == 0) {
            return false;
        }

        memoizedIsInitialized = 1;
        return true;
    }

    @java.lang.Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
        throws java.io.IOException {
        if (!getTsoBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 1, tso_);
        }
        if (type_ != com.aliyun.polardbx.binlog.protocol.TxnType.DML.getNumber()) {
            output.writeEnum(2, type_);
        }
        if (!getBeginSchemaBytes().isEmpty()) {
            com.google.protobuf.GeneratedMessageV3.writeString(output, 3, beginSchema_);
        }
        if (!payload_.isEmpty()) {
            output.writeBytes(4, payload_);
        }
        unknownFields.writeTo(output);
    }

    @java.lang.Override
    public int getSerializedSize() {
        int size = memoizedSize;
        if (size != -1) {
            return size;
        }

        size = 0;
        if (!getTsoBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, tso_);
        }
        if (type_ != com.aliyun.polardbx.binlog.protocol.TxnType.DML.getNumber()) {
            size += com.google.protobuf.CodedOutputStream
                .computeEnumSize(2, type_);
        }
        if (!getBeginSchemaBytes().isEmpty()) {
            size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, beginSchema_);
        }
        if (!payload_.isEmpty()) {
            size += com.google.protobuf.CodedOutputStream
                .computeBytesSize(4, payload_);
        }
        size += unknownFields.getSerializedSize();
        memoizedSize = size;
        return size;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof com.aliyun.polardbx.binlog.protocol.TxnMergedToken)) {
            return super.equals(obj);
        }
        com.aliyun.polardbx.binlog.protocol.TxnMergedToken other =
            (com.aliyun.polardbx.binlog.protocol.TxnMergedToken) obj;

        if (!getTso()
            .equals(other.getTso())) {
            return false;
        }
        if (type_ != other.type_) {
            return false;
        }
        if (!getBeginSchema()
            .equals(other.getBeginSchema())) {
            return false;
        }
        if (!getPayload()
            .equals(other.getPayload())) {
            return false;
        }
        if (!unknownFields.equals(other.unknownFields)) {
            return false;
        }
        return true;
    }

    @java.lang.Override
    public int hashCode() {
        if (memoizedHashCode != 0) {
            return memoizedHashCode;
        }
        int hash = 41;
        hash = (19 * hash) + getDescriptor().hashCode();
        hash = (37 * hash) + TSO_FIELD_NUMBER;
        hash = (53 * hash) + getTso().hashCode();
        hash = (37 * hash) + TYPE_FIELD_NUMBER;
        hash = (53 * hash) + type_;
        hash = (37 * hash) + BEGINSCHEMA_FIELD_NUMBER;
        hash = (53 * hash) + getBeginSchema().hashCode();
        hash = (37 * hash) + PAYLOAD_FIELD_NUMBER;
        hash = (53 * hash) + getPayload().hashCode();
        hash = (29 * hash) + unknownFields.hashCode();
        memoizedHashCode = hash;
        return hash;
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(java.io.InputStream input)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseWithIOException(PARSER, input);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseDelimitedWithIOException(PARSER, input);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseWithIOException(PARSER, input);
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
            .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @java.lang.Override
    public Builder newBuilderForType() {
        return newBuilder();
    }

    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }

    public static Builder newBuilder(com.aliyun.polardbx.binlog.protocol.TxnMergedToken prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    @java.lang.Override
    public Builder toBuilder() {
        return this == DEFAULT_INSTANCE
            ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        Builder builder = new Builder(parent);
        return builder;
    }

    /**
     * Protobuf type {@code com.aliyun.polardbx.binlog.protocol.TxnMergedToken}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:com.aliyun.polardbx.binlog.protocol.TxnMergedToken)
        com.aliyun.polardbx.binlog.protocol.TxnMergedTokenOrBuilder {
        public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
            return com.aliyun.polardbx.binlog.protocol.TxnStream.internal_static_com_aliyun_polardbx_binlog_protocol_TxnMergedToken_descriptor;
        }

        @java.lang.Override
        protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
            return com.aliyun.polardbx.binlog.protocol.TxnStream.internal_static_com_aliyun_polardbx_binlog_protocol_TxnMergedToken_fieldAccessorTable
                .ensureFieldAccessorsInitialized(
                    com.aliyun.polardbx.binlog.protocol.TxnMergedToken.class,
                    com.aliyun.polardbx.binlog.protocol.TxnMergedToken.Builder.class);
        }

        // Construct using com.aliyun.polardbx.binlog.protocol.TxnMergedToken.newBuilder()
        private Builder() {
            maybeForceBuilderInitialization();
        }

        private Builder(
            com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
            super(parent);
            maybeForceBuilderInitialization();
        }

        private void maybeForceBuilderInitialization() {
            if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
            }
        }

        @java.lang.Override
        public Builder clear() {
            super.clear();
            tso_ = "";

            type_ = 0;

            beginSchema_ = "";

            payload_ = com.google.protobuf.ByteString.EMPTY;

            return this;
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
            return com.aliyun.polardbx.binlog.protocol.TxnStream.internal_static_com_aliyun_polardbx_binlog_protocol_TxnMergedToken_descriptor;
        }

        @java.lang.Override
        public com.aliyun.polardbx.binlog.protocol.TxnMergedToken getDefaultInstanceForType() {
            return com.aliyun.polardbx.binlog.protocol.TxnMergedToken.getDefaultInstance();
        }

        @java.lang.Override
        public com.aliyun.polardbx.binlog.protocol.TxnMergedToken build() {
            com.aliyun.polardbx.binlog.protocol.TxnMergedToken result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @java.lang.Override
        public com.aliyun.polardbx.binlog.protocol.TxnMergedToken buildPartial() {
            com.aliyun.polardbx.binlog.protocol.TxnMergedToken result =
                new com.aliyun.polardbx.binlog.protocol.TxnMergedToken(this);
            result.tso_ = tso_;
            result.type_ = type_;
            result.beginSchema_ = beginSchema_;
            result.payload_ = payload_;
            onBuilt();
            return result;
        }

        @java.lang.Override
        public Builder clone() {
            return super.clone();
        }

        @java.lang.Override
        public Builder setField(
            com.google.protobuf.Descriptors.FieldDescriptor field,
            java.lang.Object value) {
            return super.setField(field, value);
        }

        @java.lang.Override
        public Builder clearField(
            com.google.protobuf.Descriptors.FieldDescriptor field) {
            return super.clearField(field);
        }

        @java.lang.Override
        public Builder clearOneof(
            com.google.protobuf.Descriptors.OneofDescriptor oneof) {
            return super.clearOneof(oneof);
        }

        @java.lang.Override
        public Builder setRepeatedField(
            com.google.protobuf.Descriptors.FieldDescriptor field,
            int index, java.lang.Object value) {
            return super.setRepeatedField(field, index, value);
        }

        @java.lang.Override
        public Builder addRepeatedField(
            com.google.protobuf.Descriptors.FieldDescriptor field,
            java.lang.Object value) {
            return super.addRepeatedField(field, value);
        }

        @java.lang.Override
        public Builder mergeFrom(com.google.protobuf.Message other) {
            if (other instanceof com.aliyun.polardbx.binlog.protocol.TxnMergedToken) {
                return mergeFrom((com.aliyun.polardbx.binlog.protocol.TxnMergedToken) other);
            } else {
                super.mergeFrom(other);
                return this;
            }
        }

        public Builder mergeFrom(com.aliyun.polardbx.binlog.protocol.TxnMergedToken other) {
            if (other == com.aliyun.polardbx.binlog.protocol.TxnMergedToken.getDefaultInstance()) {
                return this;
            }
            if (!other.getTso().isEmpty()) {
                tso_ = other.tso_;
                onChanged();
            }
            if (other.type_ != 0) {
                setTypeValue(other.getTypeValue());
            }
            if (!other.getBeginSchema().isEmpty()) {
                beginSchema_ = other.beginSchema_;
                onChanged();
            }
            if (other.getPayload() != com.google.protobuf.ByteString.EMPTY) {
                setPayload(other.getPayload());
            }
            this.mergeUnknownFields(other.unknownFields);
            onChanged();
            return this;
        }

        @java.lang.Override
        public final boolean isInitialized() {
            return true;
        }

        @java.lang.Override
        public Builder mergeFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
            com.aliyun.polardbx.binlog.protocol.TxnMergedToken parsedMessage = null;
            try {
                parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                parsedMessage = (com.aliyun.polardbx.binlog.protocol.TxnMergedToken) e.getUnfinishedMessage();
                throw e.unwrapIOException();
            } finally {
                if (parsedMessage != null) {
                    mergeFrom(parsedMessage);
                }
            }
            return this;
        }

        private java.lang.Object tso_ = "";

        /**
         * <code>string tso = 1;</code>
         *
         * @return The tso.
         */
        public java.lang.String getTso() {
            java.lang.Object ref = tso_;
            if (!(ref instanceof java.lang.String)) {
                com.google.protobuf.ByteString bs =
                    (com.google.protobuf.ByteString) ref;
                java.lang.String s = bs.toStringUtf8();
                tso_ = s;
                return s;
            } else {
                return (java.lang.String) ref;
            }
        }

        /**
         * <code>string tso = 1;</code>
         *
         * @return The bytes for tso.
         */
        public com.google.protobuf.ByteString
        getTsoBytes() {
            java.lang.Object ref = tso_;
            if (ref instanceof String) {
                com.google.protobuf.ByteString b =
                    com.google.protobuf.ByteString.copyFromUtf8(
                        (java.lang.String) ref);
                tso_ = b;
                return b;
            } else {
                return (com.google.protobuf.ByteString) ref;
            }
        }

        /**
         * <code>string tso = 1;</code>
         *
         * @param value The tso to set.
         * @return This builder for chaining.
         */
        public Builder setTso(
            java.lang.String value) {
            if (value == null) {
                throw new NullPointerException();
            }

            tso_ = value;
            onChanged();
            return this;
        }

        /**
         * <code>string tso = 1;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearTso() {

            tso_ = getDefaultInstance().getTso();
            onChanged();
            return this;
        }

        /**
         * <code>string tso = 1;</code>
         *
         * @param value The bytes for tso to set.
         * @return This builder for chaining.
         */
        public Builder setTsoBytes(
            com.google.protobuf.ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }
            checkByteStringIsUtf8(value);

            tso_ = value;
            onChanged();
            return this;
        }

        private int type_ = 0;

        /**
         * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
         *
         * @return The enum numeric value on the wire for type.
         */
        @java.lang.Override
        public int getTypeValue() {
            return type_;
        }

        /**
         * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
         *
         * @param value The enum numeric value on the wire for type to set.
         * @return This builder for chaining.
         */
        public Builder setTypeValue(int value) {

            type_ = value;
            onChanged();
            return this;
        }

        /**
         * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
         *
         * @return The type.
         */
        @java.lang.Override
        public com.aliyun.polardbx.binlog.protocol.TxnType getType() {
            @SuppressWarnings("deprecation")
            com.aliyun.polardbx.binlog.protocol.TxnType result =
                com.aliyun.polardbx.binlog.protocol.TxnType.valueOf(type_);
            return result == null ? com.aliyun.polardbx.binlog.protocol.TxnType.UNRECOGNIZED : result;
        }

        /**
         * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
         *
         * @param value The type to set.
         * @return This builder for chaining.
         */
        public Builder setType(com.aliyun.polardbx.binlog.protocol.TxnType value) {
            if (value == null) {
                throw new NullPointerException();
            }

            type_ = value.getNumber();
            onChanged();
            return this;
        }

        /**
         * <code>.com.aliyun.polardbx.binlog.protocol.TxnType type = 2;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearType() {

            type_ = 0;
            onChanged();
            return this;
        }

        private java.lang.Object beginSchema_ = "";

        /**
         * <code>string beginSchema = 3;</code>
         *
         * @return The beginSchema.
         */
        public java.lang.String getBeginSchema() {
            java.lang.Object ref = beginSchema_;
            if (!(ref instanceof java.lang.String)) {
                com.google.protobuf.ByteString bs =
                    (com.google.protobuf.ByteString) ref;
                java.lang.String s = bs.toStringUtf8();
                beginSchema_ = s;
                return s;
            } else {
                return (java.lang.String) ref;
            }
        }

        /**
         * <code>string beginSchema = 3;</code>
         *
         * @return The bytes for beginSchema.
         */
        public com.google.protobuf.ByteString
        getBeginSchemaBytes() {
            java.lang.Object ref = beginSchema_;
            if (ref instanceof String) {
                com.google.protobuf.ByteString b =
                    com.google.protobuf.ByteString.copyFromUtf8(
                        (java.lang.String) ref);
                beginSchema_ = b;
                return b;
            } else {
                return (com.google.protobuf.ByteString) ref;
            }
        }

        /**
         * <code>string beginSchema = 3;</code>
         *
         * @param value The beginSchema to set.
         * @return This builder for chaining.
         */
        public Builder setBeginSchema(
            java.lang.String value) {
            if (value == null) {
                throw new NullPointerException();
            }

            beginSchema_ = value;
            onChanged();
            return this;
        }

        /**
         * <code>string beginSchema = 3;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearBeginSchema() {

            beginSchema_ = getDefaultInstance().getBeginSchema();
            onChanged();
            return this;
        }

        /**
         * <code>string beginSchema = 3;</code>
         *
         * @param value The bytes for beginSchema to set.
         * @return This builder for chaining.
         */
        public Builder setBeginSchemaBytes(
            com.google.protobuf.ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }
            checkByteStringIsUtf8(value);

            beginSchema_ = value;
            onChanged();
            return this;
        }

        private com.google.protobuf.ByteString payload_ = com.google.protobuf.ByteString.EMPTY;

        /**
         * <code>bytes payload = 4;</code>
         *
         * @return The payload.
         */
        @java.lang.Override
        public com.google.protobuf.ByteString getPayload() {
            return payload_;
        }

        /**
         * <code>bytes payload = 4;</code>
         *
         * @param value The payload to set.
         * @return This builder for chaining.
         */
        public Builder setPayload(com.google.protobuf.ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }

            payload_ = value;
            onChanged();
            return this;
        }

        /**
         * <code>bytes payload = 4;</code>
         *
         * @return This builder for chaining.
         */
        public Builder clearPayload() {

            payload_ = getDefaultInstance().getPayload();
            onChanged();
            return this;
        }

        @java.lang.Override
        public final Builder setUnknownFields(
            final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.setUnknownFields(unknownFields);
        }

        @java.lang.Override
        public final Builder mergeUnknownFields(
            final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.mergeUnknownFields(unknownFields);
        }

        // @@protoc_insertion_point(builder_scope:com.aliyun.polardbx.binlog.protocol.TxnMergedToken)
    }

    // @@protoc_insertion_point(class_scope:com.aliyun.polardbx.binlog.protocol.TxnMergedToken)
    private static final com.aliyun.polardbx.binlog.protocol.TxnMergedToken DEFAULT_INSTANCE;

    static {
        DEFAULT_INSTANCE = new com.aliyun.polardbx.binlog.protocol.TxnMergedToken();
    }

    public static com.aliyun.polardbx.binlog.protocol.TxnMergedToken getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<TxnMergedToken>
        PARSER = new com.google.protobuf.AbstractParser<TxnMergedToken>() {
        @java.lang.Override
        public TxnMergedToken parsePartialFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
            return new TxnMergedToken(input, extensionRegistry);
        }
    };

    public static com.google.protobuf.Parser<TxnMergedToken> parser() {
        return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<TxnMergedToken> getParserForType() {
        return PARSER;
    }

    @java.lang.Override
    public com.aliyun.polardbx.binlog.protocol.TxnMergedToken getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

}
