/*
 * Copyright (c) 2008 - 2014 MongoDB, Inc.
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


package com.mongodb

import org.bson.types.BsonDocument
import org.bson.types.BsonInt32
import org.mongodb.MongoCommandFailureException
import org.mongodb.MongoDuplicateKeyException
import org.mongodb.MongoWriteException
import org.mongodb.ServerCursor
import org.mongodb.connection.ClusterDescription
import org.mongodb.connection.MongoSocketReadException
import org.mongodb.connection.ServerAddress
import org.mongodb.connection.ServerDescription
import spock.lang.Specification
import spock.lang.Unroll

import static com.mongodb.MongoExceptions.mapException
import static java.util.Arrays.asList
import static org.mongodb.connection.ClusterConnectionMode.SINGLE
import static org.mongodb.connection.ClusterType.STANDALONE
import static org.mongodb.connection.ServerConnectionState.CONNECTED

@SuppressWarnings('LineLength')
class MongoExceptionsSpecification extends Specification {
    private final static String MESSAGE = 'New style exception'
    private final static int ERROR_CODE = 500
    private final static CLUSTER_DESCRIPTION = new ClusterDescription(SINGLE, STANDALONE,
                                                                      asList(ServerDescription.builder()
                                                                                              .address(new ServerAddress())
                                                                                              .state(CONNECTED)
                                                                                              .build()))

    @Unroll
    def 'should convert #exceptionToBeMapped into #exceptionForCompatibilityApi'() {

        expect:
        MongoException actualException = mapException(exceptionToBeMapped)
        actualException.class == exceptionForCompatibilityApi
        actualException.getCause() == exceptionToBeMapped.getCause()
        actualException.getCode() == errorCode
        actualException.getMessage() == exceptionToBeMapped.getMessage()
        !(actualException.getCause() instanceof org.mongodb.MongoException)
        !actualException.getStackTrace().any { it.className.startsWith('org.mongodb') }

        where:
        exceptionToBeMapped                                                                   | exceptionForCompatibilityApi     | errorCode
        new org.mongodb.MongoInterruptedException(MESSAGE, new InterruptedException('cause')) | MongoInterruptedException        | -4
        new MongoSocketReadException(MESSAGE, new ServerAddress(), new IOException('cause'))  | MongoSocketException             | -2
        new MongoDuplicateKeyException(ERROR_CODE, MESSAGE,
                                       commandResultWithErrorCode(ERROR_CODE))                | MongoException.DuplicateKey      |
        ERROR_CODE
        new MongoCommandFailureException(commandResultWithErrorCode(ERROR_CODE))              | CommandFailureException          |
        ERROR_CODE
        new org.mongodb.MongoInternalException(MESSAGE)                                       | MongoInternalException           | -4
        new MongoWriteException(ERROR_CODE, MESSAGE, commandResultWithErrorCode(ERROR_CODE))  | WriteConcernException            |
        ERROR_CODE
        new org.mongodb.connection.MongoTimeoutException(MESSAGE)                             | MongoTimeoutException            | -3
        new org.mongodb.connection.MongoWaitQueueFullException(MESSAGE)                       | MongoWaitQueueFullException      | -3
        new org.mongodb.MongoIncompatibleDriverException('', CLUSTER_DESCRIPTION)             | MongoIncompatibleDriverException | -3
        new org.mongodb.connection.MongoServerSelectionException('timeout')                   | MongoServerSelectionException    | -3
    }

    def 'should convert MongoCursorNotFoundException into MongoException.CursorNotFound'() {
        given:
        long cursorId = 123L
        ServerAddress serverAddress = new ServerAddress()
        String expectedMessage = "Cursor $cursorId not found on server $serverAddress"

        when:
        MongoException actualException = mapException(
                new org.mongodb.MongoCursorNotFoundException(new ServerCursor(cursorId, serverAddress))
        )

        then:
        actualException instanceof MongoCursorNotFoundException
        actualException.getMessage() == expectedMessage
        MongoCursorNotFoundException actualAsCursorNotFound = (MongoCursorNotFoundException) actualException
        actualAsCursorNotFound.getCursorId() == cursorId
        actualAsCursorNotFound.getServerAddress().getHost() == serverAddress.getHost()
        actualAsCursorNotFound.getServerAddress().getPort() == serverAddress.getPort()

        !(actualException.getCause() instanceof org.mongodb.MongoException)
        !actualException.getStackTrace().any { it.className.startsWith('org.mongodb') }
    }

    def 'should convert SocketExceptions that are not IOExceptions into MongoException'() {
        given:
        String expectedMessage = 'Exception in the new architecture'

        when:
        MongoException actualException = mapException(new MongoSocketReadException(expectedMessage, new ServerAddress()))

        then:
        !(actualException instanceof MongoSocketException)
        actualException instanceof MongoException
        actualException.getMessage() == expectedMessage
        !(actualException.getCause() instanceof org.mongodb.MongoException)
        !actualException.getStackTrace().any { it.className.startsWith('org.mongodb') }
    }


    private static org.mongodb.CommandResult commandResultWithErrorCode(int expectedErrorCode) {
        new org.mongodb.CommandResult(new ServerAddress(), new BsonDocument('code', new BsonInt32(expectedErrorCode)), 15L)
    }

}
