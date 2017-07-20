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
import org.joda.time.DateTime
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyDataPersist}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SurveyWidgetMongoRepository])
trait SurveyWidgetRepository {

  /**
    * Insert surveyData into the repo - return a `Left` if there is an error while inserting,
    * otherwise return a `DataPersisted()`
    *
    * @param data The surveyData to insert
    */
  def persistData(data: SurveyDataPersist): Future[Either[String,DataPersisted]]

}


@Singleton
class SurveyWidgetMongoRepository @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[SurveyDataPersist, BSONObjectID] (
    collectionName = "survey-widgets",
    mongo = mongo.mongoConnector.db,
    SurveyDataPersist.dataFormat,
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

  override def persistData(data: SurveyDataPersist): Future[Either[String, DataPersisted]] = {
    logger.info(s"Persisting surveyData into surveyData store (${data.idString})")
    insert(data).map[Either[String,DataPersisted]]{ result ⇒
      if (!result.ok) {
        Left(s"Failed to write to surveyData store: ${result.errmsg.getOrElse("-")}. " +
          s"Write errors were ${result.writeErrors.map(_.errmsg).mkString(",")}")
      } else {
        logger.info(s"Successfully wrote to surveyData store (${data.idString})")
        Right(DataPersisted())
      }
    }.recover{ case e ⇒
      logger.error(s"Could not write to surveyData store (${data.idString})", e)
      Left(s"Failed to write to surveyData store: ${e.getMessage}")
    }
  }

}
