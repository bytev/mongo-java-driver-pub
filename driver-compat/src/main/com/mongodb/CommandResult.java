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

import static com.mongodb.DBObjects.toDBObject;
import static org.mongodb.assertions.Assertions.notNull;

/**
 * A simple wrapper to hold the result of a command.  All the fields from the response document have been added to this result.
 */
public class CommandResult extends BasicDBObject {
    private static final long serialVersionUID = 5907909423864204060L;
    private final ServerAddress host;

    CommandResult(final org.mongodb.CommandResult commandResult) {
        this(new ServerAddress(commandResult.getAddress()));
        putAll(toDBObject(commandResult.getResponse()));
    }

    CommandResult(final DBObject response, final ServerAddress serverAddress) {
        this(serverAddress);
        putAll(response);
    }

    CommandResult(final ServerAddress serverAddress) {
        host = notNull("serverAddress", serverAddress);
        // so it is shown in toString/debug
        put("serverUsed", serverAddress.toString());
    }

    /**
     * gets the "ok" field which is the result of the command
     *
     * @return True if ok
     */
    public boolean ok() {
        Object okValue = get("ok");
        if (okValue instanceof Boolean) {
            return (Boolean) okValue;
        } else if (okValue instanceof Number) {
            return ((Number) okValue).intValue() == 1;
        } else {
            return false;
        }
    }

    /**
     * Gets the error message associated with a failed command.
     *
     * @return The error message or null
     */
    public String getErrorMessage() {
        Object foo = get("errmsg");
        if (foo == null) {
            return null;
        }
        return foo.toString();
    }

    /**
     * Utility method to create an exception from a failed command.
     *
     * @return The mongo exception or null
     */
    public MongoException getException() {
        if (!ok()) {
            return new CommandFailureException(this);
        }

        return null;
    }

    /**
     * throws an exception containing the cmd name, in case the command failed, or the "err/code" information
     *
     * @throws MongoException
     */
    public void throwOnError() {
        if (!ok()) {
            throw getException();
        }
    }

    /**
     * Gets the server that the command result came from.
     *
     * @return the server address
     */
    public ServerAddress getServerUsed() {
        return host;
    }
}
