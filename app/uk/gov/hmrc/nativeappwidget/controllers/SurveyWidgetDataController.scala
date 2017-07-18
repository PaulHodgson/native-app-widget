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

package uk.gov.hmrc.nativeappwidget.controllers

import cats.syntax.either._
import com.google.inject.{Inject, Singleton}
import play.api.data.validation.ValidationError
import play.api.libs.json.JsPath
import play.api.mvc._
import play.api.Logger
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.nativeappwidget.MicroserviceAuthConnector
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyData}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SurveyWidgetDataController @Inject()(service: SurveyWidgetDataServiceAPI)(implicit ec: ExecutionContext) extends BaseController with AuthorisedFunctions {

  val logger = Logger(this.getClass)

  def addWidgetData: Action[AnyContent] = Action.async { implicit request ⇒
    parseSurveyData(request).fold(
      { e ⇒
        logger.error(s"Could not parse survey data in request: $e")
        Future.successful(BadRequest(e))
      }, { data ⇒
        service.addWidgetData(data).map{r ⇒ handleSurveyWidgetResult(r, data)}
      }
    )
  }


  private def parseSurveyData(request: Request[AnyContent]): Either[String,SurveyData] =
    request.body.asJson.fold[Either[String,SurveyData]](
      Left("Expected JSON in body")
    ){
      _.validate[SurveyData].asEither.leftMap(
        { e ⇒
          logger.error(s"Could not parse JSON in request: ${prettyPrint(e)}")
          "Invalid JSON in body"
        })
    }

  private def handleSurveyWidgetResult(result: Either[SurveyWidgetError, DataPersisted],
                                       data: SurveyData): Result = {
    result.fold(
      _ match {
        case Unauthorised ⇒
          logger.warn(s"Received request to insert survey data but the campaign ID wasn't whitelisted: ${data.idString}")
          Unauthorized
        case RepoError(message) ⇒
          logger.error(s"Could not insert into repo (${data.idString}): $message")
          InternalServerError
      },{ _ ⇒
        logger.debug(s"Successfully inserted into repo (${data.idString})")
        Ok
      }
    )
  }

  private def prettyPrint(jsErrors: Seq[(JsPath, Seq[ValidationError])]): String = jsErrors.map { case (jsPath, validationErrors) ⇒
    jsPath.toString + ": [" + validationErrors.map(_.message).mkString(",") + "]"
  }.mkString("; ")

  override def authConnector: AuthConnector = MicroserviceAuthConnector
}
