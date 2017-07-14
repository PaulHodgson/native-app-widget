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
import play.api.Logger
import play.api.libs.json.JsError
import play.api.mvc._
import uk.gov.hmrc.nativeappwidget.models.Data
import uk.gov.hmrc.nativeappwidget.repos.Repo
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class Controller @Inject()(repo: Repo)(implicit ec: ExecutionContext) extends BaseController {

  val logger = Logger(this.getClass)

  def insert: Action[AnyContent] = Action.async { implicit request ⇒
    request.body.asJson.fold(
      Future.successful(BadRequest("Expected JSON in body"))
    ){
      _.validate[Data].fold(
        { e ⇒
          logger.error(s"Could not parse JSON from request: ${prettyPrint(JsError(e))}")
          Future.successful(BadRequest("Could not parse JSON"))
        },
        repo.insertData(_).map(handleRepoInsertResult)
      )
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
