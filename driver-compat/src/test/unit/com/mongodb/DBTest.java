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

import category.ReplicaSet;
import org.bson.types.BsonDocument;
import org.bson.types.BsonInt32;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.operation.UserExistsOperation;

import java.net.UnknownHostException;

import static com.mongodb.DBObjectMatchers.hasFields;
import static com.mongodb.DBObjectMatchers.hasSubdocument;
import static com.mongodb.Fixture.getMongoClient;
import static com.mongodb.ReadPreference.primary;
import static com.mongodb.ReadPreference.secondary;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mongodb.Fixture.disableMaxTimeFailPoint;
import static org.mongodb.Fixture.enableMaxTimeFailPoint;
import static org.mongodb.Fixture.getBinding;
import static org.mongodb.Fixture.isDiscoverableReplicaSet;
import static org.mongodb.Fixture.isSharded;
import static org.mongodb.Fixture.serverVersionAtLeast;

@SuppressWarnings("deprecation")
public class DBTest extends DatabaseTestCase {
    @Test
    public void shouldGetDefaultWriteConcern() {
        assertEquals(WriteConcern.ACKNOWLEDGED, database.getWriteConcern());
    }

    @Test
    public void shouldGetDefaultReadPreference() {
        assertEquals(ReadPreference.primary(), database.getReadPreference());
    }

    @Test
    public void shouldReturnCachedCollectionObjectIfExists() {
        DBCollection collection1 = database.getCollection("test");
        DBCollection collection2 = database.getCollection("test");
        assertThat("Checking that references are equal", collection1, sameInstance(collection2));
    }

    @Test
    public void shouldDropItself() {
        // given
        String databaseName = "drop-test-" + System.nanoTime();
        DB db = getMongoClient().getDB(databaseName);
        // creates the database in a much faster way than inserting something into it
        db.getStats();
        assertThat(getMongoClient().getDatabaseNames(), hasItem(databaseName));

        // when
        db.dropDatabase();

        // then
        assertThat(getMongoClient().getDatabaseNames(), not(hasItem(databaseName)));
    }

    @Test
    public void shouldGetCollectionNames() {
        database.dropDatabase();

        String[] collectionNames = {"c1", "c2", "c3"};

        for (final String name : collectionNames) {
            database.createCollection(name, new BasicDBObject());
        }

        assertThat(database.getCollectionNames(), hasItems(collectionNames));
    }

    @Test(expected = MongoException.class)
    public void shouldReceiveAnErrorIfCreatingCappedCollectionWithoutSize() {
        database.createCollection("someName", new BasicDBObject("capped", true));
    }

    @Test
    public void shouldCreateCappedCollection() {
        collection.drop();
        database.createCollection(collectionName, new BasicDBObject("capped", true)
                                                  .append("size", 242880));
        assertTrue(database.getCollection(collectionName).isCapped());
    }

    @Test
    public void shouldCreateCappedCollectionWithMaxNumberOfDocuments() {
        collection.drop();
        DBCollection cappedCollectionWithMax = database.createCollection(collectionName, new BasicDBObject("capped", true)
                                                                                         .append("size", 242880)
                                                                                         .append("max", 10));

        assertThat(cappedCollectionWithMax.getStats(), hasSubdocument(new BasicDBObject("capped", true).append("max", 10)));

        for (int i = 0; i < 11; i++) {
            cappedCollectionWithMax.insert(new BasicDBObject("x", i));
        }
        assertThat(cappedCollectionWithMax.find().count(), is(10));
    }

    @Test
    public void shouldCreateUncappedCollection() {
        collection.drop();
        BasicDBObject creationOptions = new BasicDBObject("capped", false);
        database.createCollection(collectionName, creationOptions);

        assertFalse(database.getCollection(collectionName).isCapped());
    }

    @Test(expected = CommandFailureException.class)
    public void shouldThrowErrorIfCreatingACappedCollectionWithANegativeSize() {
        collection.drop();
        DBObject creationOptions = BasicDBObjectBuilder.start().add("capped", true)
                                                       .add("size", -20).get();
        database.createCollection(collectionName, creationOptions);
    }

    @Test(expected = DuplicateKeyException.class)
    public void shouldGetDuplicateKeyException() {
        DBObject doc = new BasicDBObject("_id", 1);
        collection.insert(doc);
        collection.insert(doc, WriteConcern.ACKNOWLEDGED);
    }

    @Test
    public void shouldDoEval() {
        assumeFalse(org.mongodb.Fixture.isAuthenticated());
        String code = "function(name, incAmount) {\n"
                      + "var doc = db.myCollection.findOne( { name : name } );\n"
                      + "doc = doc || { name : name , num : 0 , total : 0 , avg : 0 , _id: 1 };\n"
                      + "doc.num++;\n"
                      + "doc.total += incAmount;\n"
                      + "doc.avg = doc.total / doc.num;\n"
                      + "db.myCollection.save( doc );\n"
                      + "return doc;\n"
                      + "}";
        database.doEval(code, "eliot", 5);
        assertEquals(database.getCollection("myCollection").findOne(), new BasicDBObject("_id", 1.0)
                                                                       .append("avg", 5.0)
                                                                       .append("num", 1.0)
                                                                       .append("name", "eliot")
                                                                       .append("total", 5.0));
    }

    @Test(expected = MongoException.class)
    public void shouldThrowErrorwhileDoingEval() {
        String code = "function(a, b) {\n"
                      + "var doc = db.myCollection.findOne( { name : b } );\n"
                      + "}";
        database.eval(code, 1);
    }

    @Test
    public void shouldInsertDocumentsUsingEval() {
        // when
        database.eval("db." + collectionName + ".insert({name: 'Bob'})");

        // then
        assertThat(collection.find(new BasicDBObject("name", "Bob")).count(), is(1));
    }

    @Test
    public void shouldGetStats() {
        assumeFalse(isSharded());
        assertThat(database.getStats(), hasFields(new String[]{"collections", "avgObjSize", "indexes", "db", "indexSize", "storageSize"}));
    }

    @Test
    public void shouldExecuteCommand() {
        CommandResult commandResult = database.command(new BasicDBObject("isMaster", 1));
        assertThat(commandResult, hasFields(new String[]{"ismaster", "maxBsonObjectSize", "ok", "serverUsed"}));
    }

    @Test(expected = MongoExecutionTimeoutException.class)
    public void shouldTimeOutCommand() {
        assumeFalse(isSharded());
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)));
        enableMaxTimeFailPoint();
        try {
            database.command(new BasicDBObject("isMaster", 1).append("maxTimeMS", 1));
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldExecuteCommandWithReadPreference() {
        assumeFalse(isSharded());
        CommandResult commandResult = database.command(new BasicDBObject("dbStats", 1).append("scale", 1), secondary());
        assertThat(commandResult, hasFields(new String[]{"collections", "avgObjSize", "indexes", "db", "indexSize", "storageSize"}));
    }

    @Test
    public void shouldNotThrowAnExceptionOnCommandFailure() {
        CommandResult commandResult = database.command(new BasicDBObject("collStats", "a" + System.currentTimeMillis()));
        assertThat(commandResult, hasFields(new String[]{"serverUsed", "ok", "errmsg"}));
    }

    @Test
    public void shouldAddUser() {
        String userName = "jeff";
        char[] password = "123".toCharArray();
        boolean readOnly = true;
        database.addUser(userName, password, readOnly);
        assertTrue(new UserExistsOperation(database.getName(), userName).execute(getBinding()));
    }

    @Test
    public void shouldUpdateUser() {
        String userName = "jeff";

        char[] password = "123".toCharArray();
        boolean readOnly = true;
        database.addUser(userName, password, readOnly);

        char[] newPassword = "345".toCharArray();
        boolean newReadOnly = false;
        database.addUser(userName, newPassword, newReadOnly);

        assertTrue(new UserExistsOperation(database.getName(), userName).execute(getBinding()));
    }

    @Test
    public void shouldRemoveUser() {
        String userName = "jeff";

        char[] password = "123".toCharArray();
        boolean readOnly = true;
        database.addUser(userName, password, readOnly);

        database.removeUser(userName);

        assertFalse(new UserExistsOperation(database.getName(), userName).execute(getBinding()));
    }

    @Test
    public void shouldReleaseConnectionOnLastCallToRequestEndWhenRequestStartCallsAreNested() {
        database.requestStart();
        try {
            database.command(new BasicDBObject("ping", 1));
            database.requestStart();
            try {
                database.command(new BasicDBObject("ping", 1));
            } finally {
                database.requestDone();
            }
        } finally {
            database.requestDone();
        }
    }

    @Test
    public void shouldNotThrowAnExceptionWhenRequestDoneIsCalledWithoutFirstCallingRequestStart() throws UnknownHostException {
        database.requestDone();
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldNotThrowAnErrorWhenEnsureConnectionCalledAfterRequestStart() throws UnknownHostException {
        database.requestStart();
        try {
            database.requestEnsureConnection();
            database.executeCommand(new BsonDocument("ping", new BsonInt32(1)));
        } finally {
            database.requestDone();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowAnExceptionWhenDBNameContainsSpaces() {
        getMongoClient().getDB("foo bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowAnExceptionWhenDBNameIsEmpty() {
        getMongoClient().getDB("");
    }

    @Test
    public void shouldIgnoreCaseWhenCheckingIfACollectionExists() {
        // Given
        database.getCollection("foo1").drop();
        assertFalse(database.collectionExists("foo1"));

        // When
        database.createCollection("foo1", new BasicDBObject());

        // Then
        assertTrue(database.collectionExists("foo1"));
        assertTrue(database.collectionExists("FOO1"));
        assertTrue(database.collectionExists("fOo1"));

        // Finally
        database.getCollection("foo1").drop();
    }

    @Test
    public void shouldReturnFailureWithErrorMessageWhenExecutingInvalidCommand() {
        // When
        CommandResult commandResult = database.command(new BasicDBObject("NotRealCommandName", 1));

        // Then
        assertThat(commandResult.ok(), is(false));
        assertThat(commandResult.getErrorMessage(), is("no such cmd: NotRealCommandName"));
    }

    @Test
    public void shouldReturnOKWhenASimpleCommandExecutesSuccessfully() {
        // When
        CommandResult commandResult = database.command(new BasicDBObject("isMaster", 1));

        // Then
        assertThat(commandResult.ok(), is(true));
        assertThat((Boolean) commandResult.get("ismaster"), is(true));
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldRunCommandAgainstSecondaryWhenOnlySecondaryReadPreferenceSpecified() throws UnknownHostException {
        assumeTrue(isDiscoverableReplicaSet());

        // When
        CommandResult commandResult = database.command(new BasicDBObject("dbstats", 1), secondary());

        // Then
        assertThat(commandResult.ok(), is(true));
        assertThat((String) commandResult.get("serverUsed"), not(containsString(":27017")));
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldRunStringCommandAgainstSecondaryWhenSecondaryReadPreferenceSpecified() throws UnknownHostException {
        assumeTrue(isDiscoverableReplicaSet());

        // When
        CommandResult commandResult = database.command("dbstats", secondary());

        // Then
        assertThat(commandResult.ok(), is(true));
        assertThat((String) commandResult.get("serverUsed"), not(containsString(":27017")));
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldRunCommandAgainstSecondaryWhenOnlySecondaryReadPreferenceSpecifiedAlongWithEncoder() throws UnknownHostException {
        assumeTrue(isDiscoverableReplicaSet());

        // When
        CommandResult commandResult = database.command(new BasicDBObject("dbstats", 1), secondary(), DefaultDBEncoder.FACTORY.create());

        // Then
        assertThat(commandResult.ok(), is(true));
        assertThat((String) commandResult.get("serverUsed"), not(containsString(":27017")));
    }

    @Test
    @Category(ReplicaSet.class)
    public void shouldRunCommandAgainstPrimaryWhenOnlyPrimaryReadPreferenceSpecified() throws UnknownHostException {
        // When
        CommandResult commandResult = database.command(new BasicDBObject("dbstats", 1), primary());

        // Then
        assertThat(commandResult.ok(), is(true));
        assertThat((String) commandResult.get("serverUsed"), containsString(":27017"));
    }

    @Test
    @Category(ReplicaSet.class)
    @Ignore("This is intermittent, and needs fixing.")
    //TODO: worryingly broken
    public void shouldOverrideDefaultReadPreferenceWhenCommandReadPreferenceSpecified() throws UnknownHostException {
        // Given
        DB databaseWithSecondaryReadPreference = getMongoClient().getDB("DatabaseForReadPreferenceChange-" + System.nanoTime());
        databaseWithSecondaryReadPreference.setReadPreference(secondary());

        try {
            // When
            CommandResult commandResult = databaseWithSecondaryReadPreference.command(new BasicDBObject("dbstats", 1), primary());

            // Then
            assertThat(commandResult.ok(), is(true));
            assertThat((String) commandResult.get("serverUsed"), containsString(":27018"));
        } finally {
            // Finally
            databaseWithSecondaryReadPreference.dropDatabase();
        }
    }
}
