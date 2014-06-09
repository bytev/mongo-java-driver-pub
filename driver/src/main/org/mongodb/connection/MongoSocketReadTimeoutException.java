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

package org.mongodb.connection;

/**
 * This exception is thrown when there is a timeout reading a response from the socket.
 */
public class MongoSocketReadTimeoutException extends MongoSocketException {

    private static final long serialVersionUID = -7237059971254608960L;

    public MongoSocketReadTimeoutException(final String message, final ServerAddress address,
                                           final Throwable e) {

        super(message, address, e);
    }

}
