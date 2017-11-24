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

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.mvc._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.Retrievals.internalId
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class AuthorisedRequest[+A](val internalAuthId: Option[String], request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class Authorised @Inject() (authConnector: AuthConnector) extends ActionBuilder[AuthorisedRequest] with ActionRefiner[Request, AuthorisedRequest] with Results {
  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    // should pass session as well in a frontend i.e. HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers)
    authConnector.authorise(EmptyPredicate, internalId).map { internalId =>
      Right(new AuthorisedRequest(internalId, request))
    }.recover {
      case _: AuthorisationException => Left(Forbidden)
    }
  }
}
