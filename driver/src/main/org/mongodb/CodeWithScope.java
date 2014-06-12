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

import org.bson.types.Code;

/**
 * A representation of the JavaScript Code with Scope BSON type.
 *
 * @since 3.0
 */
public class CodeWithScope extends Code {

    private final Document scope;

    private static final long serialVersionUID = -6284832275113680002L;

    public CodeWithScope(final String code, final Document scope) {
        super(code);
        this.scope = scope;
    }

    public Document getScope() {
        return scope;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        CodeWithScope that = (CodeWithScope) o;

        if (scope != null ? !scope.equals(that.scope) : that.scope != null) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        return getCode().hashCode() ^ scope.hashCode();
    }
}

