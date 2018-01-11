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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, Verify}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.internalId
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisationException, NoActiveSession}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class InternalAuthIdRequest[+A](val internalAuthId: String, request: Request[A]) extends WrappedRequest[A](request)

@ImplementedBy(classOf[AuthorisedWithInternalAuthIdImpl])
trait AuthorisedWithInternalAuthId extends ActionBuilder[InternalAuthIdRequest] with ActionRefiner[Request, InternalAuthIdRequest]

@Singleton
class AuthorisedWithInternalAuthIdImpl @Inject() (authConnector: AuthConnector) extends AuthorisedWithInternalAuthId with Results {
  override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)
    authConnector.authorise(AuthProviders(GovernmentGateway, Verify), internalId).map {
      case Some(internalAuthId) =>
        Right(new InternalAuthIdRequest(internalAuthId, request))
      case None =>
        // <confluence>/display/PE/Retrievals+Reference#RetrievalsReference-internalId states
        // "always defined for Government Gateway and Verify auth providers"
        // and we have specified AuthProviders(GovernmentGateway, Verify) so internalId
        // should always be defined.
        Logger.error("Internal auth id not found")
        Left(InternalServerError("Internal id not found"))
    }.recover {
      case _: NoActiveSession => Left(Unauthorized)
      case _: AuthorisationException => Left(Forbidden)
    }
  }
}
