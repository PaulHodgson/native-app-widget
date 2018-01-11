/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, KeyValuePair, SurveyResponse}
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository.SurveyResponsePersist

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[SurveyResponseMongoRepository])
trait SurveyWidgetRepository {

  /**
    * Insert surveyData into the repo - return a `Left` if there is an error while inserting,
    * otherwise return a `DataPersisted()`
    *
    * @param data The SurveyResponse to insert
    */
  def persist(data: SurveyResponse, internalAuthId: String): Future[Either[String,DataPersisted]]

  def findByCampaignAndAuthid(campaignId: String, internalAuthId: String): Future[Either[String, List[SurveyResponse]]]

}

object SurveyWidgetRepository {

  /**
    * Represents a person's responses to a survey plus
    *
    * @param campaignId an ID which associates the surveyData to a particular survey
    * @param internalAuthid the internal auth ID identifying the person who answered the survey
    * @param surveyData the questions and answers
    */
  case class SurveyResponsePersist(campaignId: String,
                                   internalAuthid: String, // note the lowercase "i" in "Authid" - would be nice to change this to "internalAuthId" but to do so we need to ensure compatibility with existing Mongo data.
                                   surveyData: List[KeyValuePair],
                                   created: DateTime) {

    /**
      * A string suitable for identifying the surveyData in logs.
      * Note however that this does not uniquely identify a SurveyResponsePersist because the same user can respond to the same survey multiple times.
      */
    val idString: String = s"campaignId: '$campaignId', internalAuthId: '$internalAuthid'"
  }

  private[repos] object SurveyResponsePersist {
    implicit val dataFormat: Format[SurveyResponsePersist] = Json.format[SurveyResponsePersist]
  }
}


@Singleton
class SurveyResponseMongoRepository @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[SurveyResponsePersist, BSONObjectID] (
    collectionName = "survey-widgets",
    mongo = mongo.mongoConnector.db,
    SurveyResponsePersist.dataFormat,
    ReactiveMongoFormats.objectIdFormats)
    with SurveyWidgetRepository {

  def now(): DateTime = DateTime.now(DateTimeZone.UTC)

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

  override def persist(surveyResponse: SurveyResponse, internalAuthId: String): Future[Either[String, DataPersisted]] = {
    val dataToPersist = SurveyResponsePersist(surveyResponse.campaignId, internalAuthId, surveyResponse.surveyData, now())

    logger.info(s"Persisting surveyData into surveyData store (${dataToPersist.idString})")

    insert(dataToPersist).map[Either[String, DataPersisted]] { _ ⇒
      logger.info(s"Successfully wrote to surveyData store (${dataToPersist.idString})")
      Right(DataPersisted())
    }.recover {
      case NonFatal(e) ⇒
        logger.warn(s"Could not write to surveyData store (${dataToPersist.idString})", e)
        Left(s"Failed to write to surveyData store: ${e.getMessage}")
    }
  }

  override def findByCampaignAndAuthid(campaignId: String, internalAuthId: String): Future[Either[String, List[SurveyResponse]]] = {
    find(
      "internalAuthid" -> JsString(internalAuthId),
      "campaignId" -> JsString(campaignId)
    ).map { surveyResponsePersists =>
      Right(surveyResponsePersists.map(toSurveyResponse))
    }.recover {
      case NonFatal(e) ⇒
        val idString: String = s"campaignId: '$campaignId', internalAuthId: '$internalAuthId'"
        logger.warn(s"Could not read from surveyData store ($idString)", e)
        Left(s"Failed to read from surveyData store: ${e.getMessage}")
    }
  }

  private def toSurveyResponse(persist: SurveyResponsePersist) = SurveyResponse(
    campaignId = persist.campaignId,
    surveyData = persist.surveyData
  )
}
