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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyData}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SurveyWidgetMongoRepository])
trait SurveyWidgetRepository {

  /**
    * Insert data into the repo - return a `Left` if there is an error while inserting,
    * otherwise return a `DataPersisted()`
    *
    * @param data The data to insert
    */
  def persistData(data: SurveyData): Future[Either[String,DataPersisted]]

}


@Singleton
class SurveyWidgetMongoRepository @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[SurveyData, BSONObjectID] (
    collectionName = "survey-widgets",
    mongo = mongo.mongoConnector.db,
    SurveyData.dataFormat,
    ReactiveMongoFormats.objectIdFormats)
    with SurveyWidgetRepository {

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

  override def persistData(data: SurveyData): Future[Either[String, DataPersisted]] = {
    logger.info(s"Persisting data into data store (${data.idString})")
    insert(data).map[Either[String,DataPersisted]]{ result ⇒
      if (!result.ok) {
        Left(s"Failed to write to data store: ${result.errmsg.getOrElse("-")}. " +
          s"Write errors were ${result.writeErrors.map(_.errmsg).mkString(",")}")
      } else {
        logger.info(s"Successfully wrote to data store (${data.idString})")
        Right(DataPersisted())
      }
    }.recover{ case e ⇒
      logger.error(s"Could not write to data store (${data.idString})", e)
      Left(s"Failed to write to data store: ${e.getMessage}")
    }
  }

}
