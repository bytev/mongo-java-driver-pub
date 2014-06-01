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

package com.mongodb;

import org.bson.types.BsonDocumentWrapper;

class RemoveRequest extends WriteRequest {
    private final DBObject query;
    private final boolean multi;
    private final DBObjectCodec codec;

    public RemoveRequest(final DBObject query, final boolean multi, final DBObjectCodec codec) {
        this.query = query;
        this.multi = multi;
        this.codec = codec;
    }

    public DBObject getQuery() {
        return query;
    }

    public boolean isMulti() {
        return multi;
    }

    @Override
    org.mongodb.operation.WriteRequest toNew() {
        return new org.mongodb.operation.RemoveRequest(new BsonDocumentWrapper<DBObject>(query, codec)).multi(isMulti());
    }
}
