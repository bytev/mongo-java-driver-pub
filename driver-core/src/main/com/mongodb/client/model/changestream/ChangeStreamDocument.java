/*
 * Copyright 2017 MongoDB, Inc.
 *
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
 */

package com.mongodb.client.model.changestream;

import com.mongodb.MongoNamespace;
import org.bson.BsonDocument;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

/**
 * Represents the {@code $changeStream} aggregation output document.
 *
 * <p>Note: this class will not be applicable for all change stream outputs. If using custom pipelines that radically change the
 * change stream result, then an alternative document format should be used.</p>
 *
 * @param <TDocument> The type that this collection will encode the {@code fullDocument} field into.
 * @since 3.6
 */
public final class ChangeStreamDocument<TDocument> {

    @BsonId()
    private final BsonDocument resumeToken;
    @BsonProperty("ns")
    private final MongoNamespace namespace;
    private final TDocument fullDocument;
    private final BsonDocument documentKey;
    private final OperationType operationType;
    private final UpdateDescription updateDescription;

    /**
     * Creates a new instance
     *
     * @param resumeToken the resume token
     * @param namespace the namespace
     * @param documentKey a document containing the _id of the changed document
     * @param fullDocument the fullDocument
     * @param operationType the operation type
     * @param updateDescription the update description
     */
    @BsonCreator
    public ChangeStreamDocument(@BsonProperty("resumeToken") final BsonDocument resumeToken,
                                @BsonProperty("namespace") final MongoNamespace namespace,
                                @BsonProperty("fullDocument") final TDocument fullDocument,
                                @BsonProperty("documentKey") final BsonDocument documentKey,
                                @BsonProperty("operationType") final OperationType operationType,
                                @BsonProperty("updateDescription") final UpdateDescription updateDescription) {
        this.resumeToken = resumeToken;
        this.namespace = namespace;
        this.documentKey = documentKey;
        this.fullDocument = fullDocument;
        this.operationType = operationType;
        this.updateDescription = updateDescription;
    }

    /**
     * Returns the resumeToken
     *
     * @return the resumeToken
     */
    public BsonDocument getResumeToken() {
        return resumeToken;
    }

    /**
     * Returns the namespace
     *
     * @return the namespace
     */
    public MongoNamespace getNamespace() {
        return namespace;
    }

    /**
     * Returns the fullDocument
     *
     * @return the fullDocument
     */
    public TDocument getFullDocument() {
        return fullDocument;
    }

    /**
     * Returns a document containing just the _id of the changed document.
     * <p>
     * For unsharded collections this contains a single field, _id, with the
     * value of the _id of the document updated.  For sharded collections,
     * this will contain all the components of the shard key in order,
     * followed by the _id if the _id isn’t part of the shard key.
     * </p>
     *
     * @return the document key
     */
    public BsonDocument getDocumentKey() {
        return documentKey;
    }

    /**
     * Returns the operationType
     *
     * @return the operationType
     */
    public OperationType getOperationType() {
        return operationType;
    }

    /**
     * Returns the updateDescription
     *
     * @return the updateDescription
     */
    public UpdateDescription getUpdateDescription() {
        return updateDescription;
    }

    /**
     * Creates the codec for the specific ChangeStreamOutput type
     *
     * @param fullDocumentClass the class to use to represent the fullDocument
     * @param codecRegistry the codec registry
     * @param <TFullDocument> the fullDocument type
     * @return the codec
     */
    public static <TFullDocument> Codec<ChangeStreamDocument<TFullDocument>> createCodec(final Class<TFullDocument> fullDocumentClass,
                                                                                         final CodecRegistry codecRegistry) {
        return new ChangeStreamDocumentCodec<TFullDocument>(fullDocumentClass, codecRegistry);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChangeStreamDocument<?> that = (ChangeStreamDocument<?>) o;

        if (resumeToken != null ? !resumeToken.equals(that.resumeToken) : that.resumeToken != null) {
            return false;
        }
        if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null) {
            return false;
        }
        if (fullDocument != null ? !fullDocument.equals(that.fullDocument) : that.fullDocument != null) {
            return false;
        }
        if (documentKey != null ? !documentKey.equals(that.documentKey) : that.documentKey != null) {
            return false;
        }
        if (operationType != that.operationType) {
            return false;
        }
        if (updateDescription != null ? !updateDescription.equals(that.updateDescription) : that.updateDescription != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = resumeToken != null ? resumeToken.hashCode() : 0;
        result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
        result = 31 * result + (fullDocument != null ? fullDocument.hashCode() : 0);
        result = 31 * result + (documentKey != null ? documentKey.hashCode() : 0);
        result = 31 * result + (operationType != null ? operationType.hashCode() : 0);
        result = 31 * result + (updateDescription != null ? updateDescription.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChangeStreamDocument{"
                + "resumeToken=" + resumeToken
                + ", namespace=" + namespace
                + ", fullDocument=" + fullDocument
                + ", documentKey=" + documentKey
                + ", operationType=" + operationType
                + ", updateDescription=" + updateDescription
                + "}";
    }
}
