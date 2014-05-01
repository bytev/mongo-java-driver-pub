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

package org.mongodb.async.rxjava;

import org.mongodb.MongoClientOptions;
import org.mongodb.MongoClientURI;

import java.net.UnknownHostException;

/**
 * A factory for MongoClient instances whose API is adapted to work with <a href="https://github.com/Netflix/RxJava">RxJava</a>.
 *
 * @since 3.0
 */
public final class MongoClients {
    /**
     * Create a new client with the given URI and options.
     *
     * @param mongoURI the URI of the cluster to connect to
     * @param options  the options, which override the options from the URI
     * @return the client
     * @throws java.net.UnknownHostException
     */
    public static MongoClient create(final MongoClientURI mongoURI, final MongoClientOptions options)
    throws UnknownHostException {
        return new MongoClientImpl(org.mongodb.async.MongoClients.create(mongoURI, options));
    }

    private MongoClients() {
    }
}