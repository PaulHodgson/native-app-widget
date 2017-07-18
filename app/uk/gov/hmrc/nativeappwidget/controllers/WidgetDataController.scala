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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import play.api.{Logger, mvc}
import uk.gov.hmrc.nativeappwidget.services.WidgetDataService
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class BadRequestException(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 400)

trait ErrorHandling {
  self: BaseController =>
  def log(message:String) = Logger.info(s"$message")

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier) = {
    func.recover {
      case ex:BadRequestException =>
        log("BadRequest!")
        Status(ErrorBadRequest.statusCode)(Json.toJson(ErrorBadRequest))
      case e: Exception =>
        Logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.statusCode)(Json.toJson(ErrorInternalServerError))
  }
}

@Singleton
class WidgetDataController @Inject()(service: WidgetDataService)(implicit ec: ExecutionContext) extends BaseController with ErrorHandling {

  val logger = Logger(this.getClass)

  def addWidgetData: Action[AnyContent] = Action.async { implicit request ⇒
    errorWrapper {
      request.body.asJson.fold(Future.successful(BadRequest("Expected JSON in body"))){
        service.addWidgetData(_)
        ???
      }
    }
  }

  private def handleRepoInsertResult(result: Either[String,Unit]): Result = result.fold(
    { e ⇒
      logger.error(s"Could not insert into repo: $e")
      InternalServerError
    },{ _ ⇒
      logger.debug("Successfully inserted into repo")
      Ok
    })

  private def prettyPrint(jsError: JsError): String = jsError.errors.map { case (jsPath, validationErrors) ⇒
    jsPath.toString + ": [" + validationErrors.map(_.message).mkString(",") + "]"
  }.mkString("; ")


}


