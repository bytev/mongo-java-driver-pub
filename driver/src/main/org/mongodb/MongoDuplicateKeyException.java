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

/**
 * A duplicate key error.
 *
 * @since 3.0
 */
public class MongoDuplicateKeyException extends MongoWriteException {
    private static final long serialVersionUID = 3661905154229799985L;

    public MongoDuplicateKeyException(final int errorCode, final String errorMessage, final BsonDocument response,
                                      final ServerAddress serverAddress) {
        super(errorCode, errorMessage, response, serverAddress);
    }
}
