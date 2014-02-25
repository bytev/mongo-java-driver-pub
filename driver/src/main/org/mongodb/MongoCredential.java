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

package org.mongodb;


import org.mongodb.annotations.Immutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mongodb.AuthenticationMechanism.GSSAPI;
import static org.mongodb.AuthenticationMechanism.MONGODB_CR;
import static org.mongodb.AuthenticationMechanism.MONGODB_X509;
import static org.mongodb.AuthenticationMechanism.PLAIN;
import static org.mongodb.assertions.Assertions.notNull;

/**
 * Represents credentials to authenticate to a mongo server, as well as the source of the credentials and the authentication mechanism to
 * use.
 *
 * @since 3.0
 */
@Immutable
public final class MongoCredential {

    private final AuthenticationMechanism mechanism;
    private final String userName;
    private final String source;
    private final char[] password;
    private final Map<String, Object> mechanismProperties;

    /**
     * Creates a MongoCredential instance for the MongoDB Challenge Response protocol.
     *
     * @param userName the non-null user name
     * @param database the non-null database where the user is defined
     * @param password the non-null password
     * @return the credential
     */
    public static MongoCredential createMongoCRCredential(final String userName, final String database, final char[] password) {
        return new MongoCredential(MONGODB_CR, userName, database, password);
    }

    /**
     * Creates a MongoCredential instance for the MongoDB X.509 protocol.
     *
     * @param userName the non-null user name
     * @return the credential
     */
    public static MongoCredential createMongoX509Credential(final String userName) {
        return new MongoCredential(MONGODB_X509, userName, "$external", null);
    }

    /**
     * Creates a MongoCredential instance for the GSSAPI SASL mechanism.  To override the default service name of {@code "mongodb"}, add a
     * mechanism property with the name {@code "SERVICE_NAME"}. To force canonicalization of the host name prior to authentication, add a
     * mechanism property with the name {@code "CANONICALIZE_HOST_NAME"} with the value{@code true}.
     *
     * @param userName the non-null user name
     * @return the credential
     * @see #withMechanismProperty(String, Object)
     */
    public static MongoCredential createGSSAPICredential(final String userName) {
        return new MongoCredential(GSSAPI, userName, "$external", null);
    }

    /**
     * Creates a MongoCredential instance for the PLAIN SASL mechanism.
     *
     * @param userName the non-null user name
     * @param source   the non-null source where the user is defined. This can be either {@code "$external"} or the name of a database.
     * @param password the non-null user password
     * @return the credential
     */
    public static MongoCredential createPlainCredential(final String userName, final String source, final char[] password) {
        return new MongoCredential(PLAIN, userName, source, password);
    }

    /**
     * Creates a new MongoCredential as a copy of this instance, with the specified mechanism property added.
     *
     * @param key   the key to the property
     * @param value the value of the property
     * @param <T>   the property type
     * @return the credential
     */
    public <T> MongoCredential withMechanismProperty(final String key, final T value) {
        return new MongoCredential(this, key, value);
    }

    /**
     * Constructs a new instance using the given mechanism, userName, source, and password
     *
     * @param mechanism the authentication mechanism
     * @param userName  the user name
     * @param source    the source of the user name, typically a database name
     * @param password  the password
     */
    MongoCredential(final AuthenticationMechanism mechanism, final String userName, final String source, final char[] password) {
        this.mechanism = notNull("mechanism", mechanism);
        this.userName = notNull("userName", userName);
        this.source = notNull("source", source);

        if ((mechanism == PLAIN || mechanism == MONGODB_CR) && password == null) {
            throw new IllegalArgumentException("Password can not be null for " + mechanism + " mechanism");
        }

        if ((mechanism == GSSAPI || mechanism == MONGODB_X509) && password != null) {
            throw new IllegalArgumentException("Password must be null for the " + mechanism + " mechanism");
        }

        this.password = password != null ? password.clone() : null;
        this.mechanismProperties = Collections.emptyMap();
    }

    /**
     * Constructs a new instance using the given credential plus an additional mechanism property.
     *
     * @param from                   the credential to copy from
     * @param mechanismPropertyKey   the new mechanism property key
     * @param mechanismPropertyValue the new mechanism property value
     * @param <T>                    the mechanism property type
     */
    <T> MongoCredential(final MongoCredential from, final String mechanismPropertyKey, final T mechanismPropertyValue) {
        notNull("mechanismPropertyKey", mechanismPropertyKey);

        this.mechanism = from.mechanism;
        this.userName = from.userName;
        this.source = from.source;
        this.password = from.password;
        this.mechanismProperties = new HashMap<String, Object>(from.mechanismProperties);
        this.mechanismProperties.put(mechanismPropertyKey, mechanismPropertyValue);
    }

    /**
     * Gets the mechanism
     *
     * @return the mechanism.
     */
    public AuthenticationMechanism getMechanism() {
        return mechanism;
    }

    /**
     * Gets the user name
     *
     * @return the user name.  Can never be null.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Gets the source of the user name, typically the name of the database where the user is defined.
     *
     * @return the user name.  Can never be null.
     */
    public String getSource() {
        return source;
    }

    /**
     * Gets the password.
     *
     * @return the password.  Can be null for some mechanisms.
     */
    public char[] getPassword() {
        if (password == null) {
            return null;
        }
        return password.clone();
    }


    /**
     * Get the value of the given key to a mechanism property, or defaultValue if there is no mapping.
     *
     * @param key          the mechanism property key
     * @param defaultValue the default value, if no mapping exists
     * @param <T>          the value type
     * @return the mechanism property value
     */
    @SuppressWarnings("unchecked")
    public <T> T getMechanismProperty(final String key, final T defaultValue) {
        notNull("key", key);

        T value = (T) mechanismProperties.get(key);
        return (value == null) ? defaultValue : value;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MongoCredential that = (MongoCredential) o;

        if (!mechanism.equals(that.mechanism)) {
            return false;
        }
        if (!Arrays.equals(password, that.password)) {
            return false;
        }
        if (!source.equals(that.source)) {
            return false;
        }
        if (!userName.equals(that.userName)) {
            return false;
        }
        if (!mechanismProperties.equals(that.mechanismProperties)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mechanism.hashCode();
        result = 31 * result + userName.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + (password != null ? Arrays.hashCode(password) : 0);
        result = 31 * result + mechanismProperties.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MongoCredential{"
               + "mechanism=" + mechanism
               + ", userName='" + userName + '\''
               + ", source='" + source + '\''
               + ", password=<hidden>"
               + ", mechanismProperties=" + mechanismProperties
               + '}';
    }
}
