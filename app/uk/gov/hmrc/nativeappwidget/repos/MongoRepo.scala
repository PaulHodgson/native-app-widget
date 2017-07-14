/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.nativeappwidget.repos

import com.google.inject.{Inject, Singleton}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nativeappwidget.models.Data

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MongoRepo @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[Data, BSONObjectID] (
    collectionName = "survey-widgets",
    mongo = mongo.mongoConnector.db,
    Data.dataFormat,
    ReactiveMongoFormats.objectIdFormats)
    with Repo {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("campaignId" → IndexType.Ascending),
      name = Some("campaignIdIndex")
    ),
    Index(
      key = Seq("internalAuthId" → IndexType.Ascending),
      name = Some("internalAuthIdIndex")
    )
  )

  override def insertData(data: Data): Future[Either[String, Unit]] = {
    logger.info(s"Putting data for ${data.campaignId} into data store")
    insert(data).map[Either[String,Unit]]{ result ⇒
      if (!result.ok) {
        Left(s"Failed to write to enrolments store: ${result.errmsg.getOrElse("-")}. " +
          s"Write errors were ${result.writeErrors.map(_.errmsg).mkString(",")}")
      } else {
        logger.info("Successfully wrote to enrolment store")
        Right(())
      }
    }.recover{ case e ⇒
      logger.error(s"Could not write to enrolment store", e)
      Left(s"Failed to write to enrolments store: ${e.getMessage}")
    }
  }

}
