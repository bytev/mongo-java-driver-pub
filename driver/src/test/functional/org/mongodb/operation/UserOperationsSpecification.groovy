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

package org.mongodb.operation

import org.mongodb.Document
import org.mongodb.Fixture
import org.mongodb.FunctionalSpecification
import org.mongodb.codecs.DocumentCodec
import org.mongodb.connection.ClusterSettings
import org.mongodb.connection.ConnectionPoolSettings
import org.mongodb.connection.DefaultClusterFactory
import org.mongodb.connection.MongoSecurityException
import org.mongodb.connection.ServerSettings
import org.mongodb.connection.SocketSettings
import org.mongodb.connection.SocketStreamFactory
import org.mongodb.selector.PrimaryServerSelector

import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.Assume.assumeTrue
import static org.mongodb.Fixture.getPrimary
import static org.mongodb.Fixture.getSSLSettings
import static org.mongodb.Fixture.getSession
import static org.mongodb.MongoCredential.createMongoCRCredential
import static org.mongodb.WriteConcern.ACKNOWLEDGED

class UserOperationsSpecification extends FunctionalSpecification {
    private User readOnlyUser
    private User readWriteUser

    def setup() {
        readOnlyUser = new User(createMongoCRCredential('jeff', databaseName, '123'.toCharArray()), true)
        readWriteUser = new User(createMongoCRCredential('jeff', databaseName, '123'.toCharArray()), false)
    }

    def 'an added user should be found'() {
        given:
        new CreateUserOperation(readOnlyUser).execute(getSession())

        when:
        def found = new UserExistsOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())

        then:
        found

        cleanup:
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
    }

    def 'an added user should be found asynchronously'() {
        assumeTrue(Fixture.mongoClientURI.options.isAsyncEnabled())

        given:
        new CreateUserOperation(readOnlyUser).executeAsync(getSession()).get()

        when:
        def found = new UserExistsOperation(databaseName, readOnlyUser.credential.userName)
                .executeAsync(getSession()).get()

        then:
        found

        cleanup:
        new DropUserOperation(databaseName, readOnlyUser.credential.userName)
                .executeAsync(getSession()).get()
    }

    def 'an added user should authenticate'() {
        given:
        new CreateUserOperation(readOnlyUser).execute(getSession())
        def cluster = getCluster()

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        def connection = server.getConnection()

        then:
        connection

        cleanup:
        connection?.close()
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
        cluster?.close()
    }

    def 'an added user should authenticate asynchronously'() {
        assumeTrue(Fixture.mongoClientURI.options.isAsyncEnabled())

        given:
        new CreateUserOperation(readOnlyUser).executeAsync(getSession()).get()
        def cluster = getCluster()

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        def connection = server.getConnection()

        then:
        connection

        cleanup:
        connection?.close()
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).executeAsync(getSession()).get()
        cluster?.close()
    }

    def 'a removed user should not authenticate'() {
        given:
        new CreateUserOperation(readOnlyUser).execute(getSession())
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
        def cluster = getCluster()

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        server.getConnection()

        then:
        thrown(MongoSecurityException)

        cleanup:
        cluster?.close()
    }

    def 'a removed user should not authenticate asynchronously'() {
        assumeTrue(Fixture.mongoClientURI.options.isAsyncEnabled())

        given:
        new CreateUserOperation(readOnlyUser).executeAsync(getSession()).get()
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).executeAsync(getSession()).get()
        def cluster = getCluster()

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        server.getConnection()

        then:
        thrown(MongoSecurityException)

        cleanup:
        cluster?.close()
    }

    def 'a replaced user should authenticate with its new password'() {
        given:
        new CreateUserOperation(readOnlyUser).execute(getSession())
        def newUser = new User(createMongoCRCredential(readOnlyUser.credential.userName, readOnlyUser.credential.source,
                '234'.toCharArray()), true)
        new UpdateUserOperation(newUser).execute(getSession())
        def cluster = getCluster(newUser)

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        def connection = server.getConnection()

        then:
        connection

        cleanup:
        connection?.close()
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
        cluster?.close()
    }

    def 'a replaced user should authenticate with its new password asynchronously'() {
        assumeTrue(Fixture.mongoClientURI.options.isAsyncEnabled())

        given:
        new CreateUserOperation(readOnlyUser).executeAsync(getSession()).get()
        def newUser = new User(createMongoCRCredential(readOnlyUser.credential.userName, readOnlyUser.credential.source,
                '234'.toCharArray()), true)
        new UpdateUserOperation(newUser).executeAsync(getSession()).get()
        def cluster = getCluster(newUser)

        when:
        def server = cluster.selectServer(new PrimaryServerSelector(), 1, SECONDS)
        def connection = server.getConnection()

        then:
        connection

        cleanup:
        connection?.close()
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).executeAsync(getSession()).get()
        cluster?.close()
    }

    def 'a read write user should be able to write'() {
        given:
        new CreateUserOperation(readWriteUser).execute(getSession())
        def cluster = getCluster()

        when:
        def result = new InsertOperation<Document>(getNamespace(), true, ACKNOWLEDGED,
                asList(new InsertRequest<Document>(new Document())),
                new DocumentCodec())
                .execute(getSession())
        then:
        result.getCount() == 0

        cleanup:
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
        cluster?.close()
    }

    def 'a read write user should be able to write asynchronously'() {
        assumeTrue(Fixture.mongoClientURI.options.isAsyncEnabled())

        given:
        new CreateUserOperation(readWriteUser).executeAsync(getSession()).get()
        def cluster = getCluster()

        when:
        def result = new InsertOperation<Document>(getNamespace(), true, ACKNOWLEDGED,
                asList(new InsertRequest<Document>(new Document())),
                new DocumentCodec())
                .executeAsync(getSession()).get()
        then:
        result.getCount() == 0

        cleanup:
        new DropUserOperation(databaseName, readOnlyUser.credential.userName).executeAsync(getSession()).get()
        cluster?.close()
    }

//    // This test is in UserOperationTest because the assertion is conditional on auth being enabled, and
//    // there's no way to do that in Spock
//    def 'a read only user should not be able to write'() {
//        given:
//        new CreateUserOperation(readOnlyUser).execute(getSession())
//        def cluster = getCluster()
//
//        when:
//        new InsertOperation<Document>(getNamespace(), true, ACKNOWLEDGED,
//                asList(new InsertRequest<Document>(new Document())),
//                new DocumentCodec())
//                .execute(getSession())
//
//        then:
//        thrown(MongoWriteException)
//
//        cleanup:
//        new DropUserOperation(databaseName, readOnlyUser.credential.userName).execute(getSession())
//        cluster?.close()
//    }

    def getCluster() {
        getCluster(readOnlyUser)
    }

    def getCluster(User user) {
        def streamFactory = new SocketStreamFactory(SocketSettings.builder().build(), getSSLSettings())
        new DefaultClusterFactory().create(ClusterSettings.builder().hosts(asList(getPrimary())).build(),
                ServerSettings.builder().build(),
                ConnectionPoolSettings.builder().maxSize(1).maxWaitQueueSize(1).build(),
                streamFactory, streamFactory, asList(user.credential), null, null, null)
    }
}
