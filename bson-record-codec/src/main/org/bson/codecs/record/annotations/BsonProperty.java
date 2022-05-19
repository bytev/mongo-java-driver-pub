/*
 * Copyright 2008-present MongoDB, Inc.
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

package org.bson.codecs.record.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that configures a record component.
 *
 * @since 4.6
 * @deprecated Prefer {@link org.bson.codecs.pojo.annotations.BsonProperty}
 */
@Deprecated
@Documented
@Target({ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface BsonProperty {
    /**
     * The field name of the record component.
     *
     * @return the field name to use for the record component
     */
    String value() default "";
}
