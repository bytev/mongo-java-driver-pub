/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb;

import org.bson.BsonDocument;
import org.mongodb.connection.ServerAddress;

import static java.lang.String.format;

/**
 * Exception indicating a failure to successfully complete a write operation according to the WriteConcern used for the operation.
 */
public class MongoWriteException extends MongoServerException {
    private static final long serialVersionUID = -1139302724723542251L;

    private final int code;
    private final String message;
    private final BsonDocument response;

    public <T> MongoWriteException(final int code, final String message, final BsonDocument response, final ServerAddress serverAddress) {
        super(format("Write failed with error code %d and error message '%s'", code, message), serverAddress);
        this.code = code;
        this.message = message;
        this.response = response;
    }

    @Override
    public int getErrorCode() {
        return code;
    }

    @Override
    public String getErrorMessage() {
        return message;
    }

    public BsonDocument getResponse() {
        return response;
    }
}
