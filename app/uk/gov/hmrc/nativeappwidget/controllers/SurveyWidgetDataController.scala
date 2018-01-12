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

package uk.gov.hmrc.nativeappwidget.controllers

import cats.syntax.either._
import com.google.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Json}
import play.api.mvc._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, Response, SurveyResponse}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SurveyWidgetDataController @Inject()(
  logger: LoggerLike,
  service: SurveyWidgetDataServiceAPI,
  authorisedWithInternalAuthId: AuthorisedWithInternalAuthId)
  (implicit ec: ExecutionContext) extends BaseController {

  def deprecatedAddWidgetData(ignored: Nino): Action[AnyContent] = addWidgetData()

  def addWidgetData(): Action[AnyContent] = authorisedWithInternalAuthId.async { implicit request ⇒
    parseSurveyData(request).fold(
      { e ⇒
        logger.warn(s"Could not parse survey surveyData in request: $e")
        Future.successful(BadRequest(e))
      }, { data ⇒
        service.addWidgetData(data, request.internalAuthId).map { r ⇒
          handleSurveyWidgetResult(r, data, request.internalAuthId)
        }
      }
    )
  }

  private def parseSurveyData(request: Request[AnyContent]): Either[String, SurveyResponse] =
    request.body.asJson.fold[Either[String,SurveyResponse]](
      Left("Expected JSON in body")
    ){
      _.validate[SurveyResponse].asEither.leftMap(
        { e ⇒
          logger.warn(s"Could not parse JSON in request: ${prettyPrint(e)}")
          "Invalid JSON in body"
        })
    }

  private def handleSurveyWidgetResult(result: Either[SurveyWidgetError, DataPersisted], data: SurveyResponse,
                                       internalAuthId: String): Result = {
    val idString = s"campaignId: '${data.campaignId}', internalAuthId: '$internalAuthId'"
    result.fold(
      {
        case SurveyWidgetError.Forbidden ⇒
          logger.warn(s"Received request to insert survey surveyData but the campaign ID wasn't whitelisted: $idString")
          Forbidden(Json.toJson(Response(FORBIDDEN)))
        case SurveyWidgetError.RepoError(message) ⇒
          logger.warn(s"Could not insert into repo ($idString): $message")
          InternalServerError(Json.toJson(Response(INTERNAL_SERVER_ERROR)))
      }, { _ ⇒
        logger.debug(s"Successfully inserted into repo ($idString)")
        Ok(Json.toJson(Response(OK)))
      }
    )
  }

  private def prettyPrint(jsErrors: Seq[(JsPath, Seq[ValidationError])]): String = jsErrors.map { case (jsPath, validationErrors) ⇒
    jsPath.toString + ": [" + validationErrors.map(_.message).mkString(",") + "]"
  }.mkString("; ")

  def getAnswers(campaignId: String, questionKey: String): Action[AnyContent] = authorisedWithInternalAuthId.async { implicit request =>
    service.getAnswers(campaignId, request.internalAuthId, questionKey).map {
      _.fold({
        case SurveyWidgetError.Forbidden =>
          Forbidden
        case SurveyWidgetError.RepoError(message) =>
          val idString = s"campaignId: '$campaignId', internalAuthId: '${request.internalAuthId}', questionKey: '$questionKey'"
          logger.warn(s"Could not read from repo ($idString): $message")
          InternalServerError
      }, { answers =>
        Ok(Json.toJson(answers))
      })
    }
  }

}

