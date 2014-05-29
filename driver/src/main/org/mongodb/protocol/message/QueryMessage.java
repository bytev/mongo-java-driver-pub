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

package org.mongodb.protocol.message;

import org.bson.io.OutputBuffer;
import org.mongodb.Document;
import org.bson.codecs.Encoder;
import org.mongodb.operation.QueryFlag;

import java.util.EnumSet;

public class QueryMessage extends BaseQueryMessage {
    private final Encoder<Document> encoder;
    private final Document queryDocument;
    private final Document fields;

    public QueryMessage(final String collectionName, final EnumSet<QueryFlag> queryFlags, final int skip,
                        final int numberToReturn, final Document queryDocument,
                        final Document fields, final Encoder<Document> encoder, final MessageSettings settings) {
        super(collectionName, queryFlags, skip, numberToReturn, settings);
        this.queryDocument = queryDocument;
        this.fields = fields;
        this.encoder = encoder;
    }

    @Override
    protected RequestMessage encodeMessageBody(final OutputBuffer buffer, final int messageStartPosition) {
        writeQueryPrologue(buffer);
        addDocument(queryDocument, encoder, buffer);
        if (fields != null) {
            addDocument(fields, encoder, buffer);
        }
        return null;
    }
}
