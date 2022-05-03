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
package org.mongodb.scala.model.search

import org.bson.BsonDocument
import org.mongodb.scala.{ BaseSpec, MongoClient }
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.search.SearchFuzzy.defaultSearchFuzzy
import org.mongodb.scala.model.search.SearchOperator.{ compound, exists, text }
import org.mongodb.scala.model.search.SearchPath.{ fieldPath, wildcardPath }

import scala.collection.JavaConverters._

class SearchOperatorSpec extends BaseSpec {
  it should "render all operators" in {
    toDocument(
      compound()
        .should(Seq(
          exists(fieldPath("fieldName1")),
          text("term1", fieldPath("fieldName2")),
          text(Seq("term2", "term3"), Seq(wildcardPath("wildc*rd")))
            .fuzzy(defaultSearchFuzzy()
              .maxEdits(1)
              .prefixLength(2)
              .maxExpansions(3))
        ).asJava)
        .minimumShouldMatch(1)
        .mustNot(Seq(
          compound().must(Seq(exists(fieldPath("fieldName3"))).asJava)
        ).asJava)
    ) should equal(
      Document("""{
        "compound": {
          "should": [
            { "exists": { "path": "fieldName1" } },
            { "text": { "query": "term1", "path": "fieldName2" } },
            { "text": { "query": [ "term2", "term3" ], "path": { "wildcard": "wildc*rd" },
              "fuzzy": { "maxEdits": 1, "prefixLength": 2, "maxExpansions": 3 } } },
          ],
          "minimumShouldMatch": 1,
          "mustNot": [
            { "compound": { "must": [ { "exists": { "path": "fieldName3" } } ] } }
          ]
        }
      }""")
    )
  }

  def toDocument(bson: Bson): Document =
    Document(bson.toBsonDocument(classOf[BsonDocument], MongoClient.DEFAULT_CODEC_REGISTRY))
}
