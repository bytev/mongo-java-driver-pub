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

package org.mongodb.operation;

import org.bson.types.BsonArray;
import org.bson.types.BsonDocument;
import org.bson.types.BsonDocumentWrapper;
import org.bson.types.BsonValue;

import java.util.ArrayList;
import java.util.List;

final class BsonDocumentWrapperHelper {

    @SuppressWarnings("unchecked")
    static <T> List<T> toList(final BsonArray array) {
        List<T> list = new ArrayList<T>();
        for (BsonValue cur : array) {
            list.add(((BsonDocumentWrapper<T>) cur).getWrappedDocument());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    static <T> T toDocument(final BsonDocument document) {
        if (document == null) {
            return null;
        }
        return ((BsonDocumentWrapper<T>) document).getWrappedDocument();
    }

    private BsonDocumentWrapperHelper() {
    }
}
