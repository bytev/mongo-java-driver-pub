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

import org.bson.types.Binary;
import org.bson.types.BsonBoolean;
import org.bson.types.BsonDocument;
import org.bson.types.BsonInt32;
import org.bson.types.BsonString;
import org.mongodb.CommandResult;
import org.mongodb.MongoCredential;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import static org.mongodb.connection.CommandHelper.executeCommand;

abstract class SaslAuthenticator extends Authenticator {

    SaslAuthenticator(final MongoCredential credential, final InternalConnection internalConnection) {
        super(credential, internalConnection);
    }

    public void authenticate() {
        SaslClient saslClient = createSaslClient();
        try {
            byte[] response = (saslClient.hasInitialResponse() ? saslClient.evaluateChallenge(new byte[0]) : null);
            CommandResult res = sendSaslStart(response);

            BsonInt32 conversationId = (BsonInt32) res.getResponse().get("conversationId");

            while (!((BsonBoolean) res.getResponse().get("done")).getValue()) {
                response = saslClient.evaluateChallenge(((Binary) res.getResponse().get("payload")).getData());

                if (response == null) {
                    throw new MongoSecurityException(getCredential(),
                                                     "SASL protocol error: no client response to challenge for credential "
                                                     + getCredential()
                    );
                }

                res = sendSaslContinue(conversationId, response);
            }
        } catch (Exception e) {
            throw new MongoSecurityException(getCredential(), "Exception authenticating " + getCredential(), e);
        } finally {
            disposeOfSaslClient(saslClient);
        }
    }

    public abstract String getMechanismName();

    protected abstract SaslClient createSaslClient();

    private CommandResult sendSaslStart(final byte[] outToken) {
        return executeCommand(getCredential().getSource(), createSaslStartCommandDocument(outToken), getInternalConnection());
    }

    private CommandResult sendSaslContinue(final BsonInt32 conversationId, final byte[] outToken) {
        return executeCommand(getCredential().getSource(), createSaslContinueDocument(conversationId, outToken), getInternalConnection());
    }

    private BsonDocument createSaslStartCommandDocument(final byte[] outToken) {
        return new BsonDocument("saslStart", new BsonInt32(1)).append("mechanism", new BsonString(getMechanismName()))
                                                              .append("payload", new Binary(outToken != null ? outToken : new byte[0]));
    }

    private BsonDocument createSaslContinueDocument(final BsonInt32 conversationId, final byte[] outToken) {
        return new BsonDocument("saslContinue", new BsonInt32(1)).append("conversationId", conversationId)
                                                                 .append("payload", new Binary(outToken));
    }

    private void disposeOfSaslClient(final SaslClient saslClient) {
        try {
            saslClient.dispose();
        } catch (SaslException e) { // NOPMD
            // ignore
        }
    }
}

