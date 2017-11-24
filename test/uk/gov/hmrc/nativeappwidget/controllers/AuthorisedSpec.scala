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

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.JsString
import play.api.mvc.Results
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException}
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthorisedSpec extends UnitSpec with MockFactory with Retrievals with Results {
  "Authorised" should {
    "include the internal auth ID in the request" in {
      val authConnectorStub = stub[AuthConnector]
      (authConnectorStub.authorise[Option[String]](_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
        .when(EmptyPredicate, internalId, *, *)
        .returns(Future successful Some("some-internal-auth-id"))

      val authorised = new Authorised(authConnectorStub)

      val action = authorised { request =>
        Ok(JsString(request.internalAuthId.get))
      }

      await(action(FakeRequest())) shouldBe Ok(JsString("some-internal-auth-id"))
    }

    "return 403 when AuthConnector throws an AuthorisationException" in {
      val authConnectorStub = stub[AuthConnector]
      (authConnectorStub.authorise[Option[String]](_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
        .when(EmptyPredicate, internalId, *, *)
        .returns(Future failed new AuthorisationException("not authorised") {})

      val authorised = new Authorised(authConnectorStub)

      val action = authorised { _ =>
        Ok
      }

      await(action(FakeRequest())) shouldBe Forbidden
    }
  }
}
