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

package uk.gov.hmrc.nativeappwidget.services

import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.nativeappwidget.models.{Content, DataPersisted, SurveyResponse}
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{Forbidden, RepoError}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SurveyWidgetDataService])
trait SurveyWidgetDataServiceAPI {

  def addWidgetData(data: SurveyResponse,
                    internalAuthId: String
                   ): Future[Either[SurveyWidgetError, DataPersisted]]

  def getAnswers(campaignId: String, internalAuthId: String, questionKey: String): Future[Either[SurveyWidgetError, Seq[Content]]]

}

object SurveyWidgetDataServiceAPI {

  sealed trait SurveyWidgetError

  object SurveyWidgetError {
    case object Forbidden extends SurveyWidgetError
    case class RepoError(message: String) extends SurveyWidgetError
  }

}

@Singleton
class SurveyWidgetDataService @Inject()(repo: SurveyWidgetRepository,
                                        configuration: Configuration)(implicit ec: ExecutionContext) extends SurveyWidgetDataServiceAPI {

  val whitelistedSurveys: Set[String] =
    configuration.underlying.getStringList("widget.surveys").asScala.toSet

  def addWidgetData(data: SurveyResponse, internalAuthId: String): Future[Either[SurveyWidgetError, DataPersisted]] =
    whitelistedSurveysOnly(data.campaignId) {
      repo.persist(data, internalAuthId).map(_.leftMap(RepoError))
    }

  def getAnswers(campaignId: String, internalAuthId: String, questionKey: String): Future[Either[SurveyWidgetError, Seq[Content]]] =
    whitelistedSurveysOnly(campaignId) {
      repo.findByCampaignAndAuthid(campaignId, internalAuthId).map {
        case Right(responses) =>
          Right(responses.flatMap(_.surveyData).filter(_.key == questionKey).map(_.value))
        case Left(message) =>
          Left(RepoError(message))
      }
    }

  private def whitelistedSurveysOnly[R](campaignId: String)(body: => Future[Either[SurveyWidgetError, R]]) =
    if(whitelistedSurveys.contains(campaignId)) {
      body
    } else {
      Future.successful(Left(Forbidden))
    }

}
