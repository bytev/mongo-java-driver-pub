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

import org.bson.types.BsonDocument;
import org.bson.types.BsonInt32;
import org.bson.types.BsonString;
import org.mongodb.AuthenticationMechanism;
import org.mongodb.MongoCommandFailureException;
import org.mongodb.MongoCredential;

import static org.mongodb.connection.CommandHelper.executeCommand;

class X509Authenticator extends Authenticator {
    X509Authenticator(final MongoCredential credential, final InternalConnection internalConnection) {
        super(credential, internalConnection);
    }

    @Override
    void authenticate() {
        try {
            BsonDocument authCommand = getAuthCommand(getCredential().getUserName());
            executeCommand(getCredential().getSource(), authCommand, getInternalConnection());
        } catch (MongoCommandFailureException e) {
            throw new MongoSecurityException(getCredential(), "Exception authenticating", e);
        }
    }

    private BsonDocument getAuthCommand(final String userName) {
        BsonDocument cmd = new BsonDocument();

        cmd.put("authenticate", new BsonInt32(1));
        cmd.put("user", new BsonString(userName));
        cmd.put("mechanism", new BsonString(AuthenticationMechanism.MONGODB_X509.getMechanismName()));

        return cmd;
    }
}
