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

import akka.util.Timeout
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.Json.JsValueWrapper
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.api.commands.{DefaultWriteResult, WriteError, WriteResult}
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyResponse, randomData}
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository.SurveyResponsePersist

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps


class SurveyWidgetMongoRepositorySpec extends WordSpec with Matchers with MockFactory with OneInstancePerTest with FutureAwaits with DefaultAwaitTimeout {

  override implicit def defaultAwaitTimeout: Timeout = 5 seconds

  trait MockDBFunctions {
    def insert[A, B](a: A): Future[B]
    def find(query: (String, JsValueWrapper)*): Future[List[SurveyResponsePersist]]
  }

  val mockDBFunctions: MockDBFunctions = mock[MockDBFunctions]

  val mockMongo: ReactiveMongoComponent = mock[ReactiveMongoComponent]

  val artificialNow = new DateTime(2000, 1, 1,13, 0)

  val repo: SurveyResponseMongoRepository = {
    // when we start SurveyWidgetMongoRepository there will some calls made by the ReactiveRepository
    // class it extends which we can't control - but we don't care about those calls.
    // Deal with them in the lines below
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mockMongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)

    new SurveyResponseMongoRepository(mockMongo) {

      override def now(): DateTime = artificialNow

      override def indexes: Seq[Index] = Seq.empty[Index]

      override def insert(entity: SurveyResponsePersist)(implicit ec: ExecutionContext): Future[WriteResult] =
        mockDBFunctions.insert[SurveyResponsePersist, WriteResult](entity)

      override def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[SurveyResponsePersist]] =
        mockDBFunctions.find(query: _*)

    }
  }

  "persist" should {

    def toDataPersist(data: SurveyResponse, internalAuthId: String): SurveyResponsePersist =
      SurveyResponsePersist(data.campaignId, internalAuthId, data.surveyData, artificialNow)

    val data = randomData()

    val internalAuthId = "id"

    def persist(data: SurveyResponse, internalAuthId: String): Either[String, DataPersisted] =
      Await.result(repo.persist(data, internalAuthId), 5.seconds)

    val successfulWriteResult = DefaultWriteResult(ok = true, 0, Seq.empty[WriteError], None, None, None)

    "insert into the mongodb collection" in {
      mockDBFunctions.insert[SurveyResponsePersist, WriteResult] _ expects toDataPersist(data, internalAuthId) returning Future.successful(successfulWriteResult)

      persist(data, internalAuthId)
    }

    "return successfully if the write was successful" in {
      mockDBFunctions.insert[SurveyResponsePersist, WriteResult] _ expects toDataPersist(data, internalAuthId) returning Future.successful(successfulWriteResult)

      persist(data, internalAuthId) shouldBe Right(DataPersisted())
    }

    "return an error when the future returned by mongo fails" in {
      mockDBFunctions.insert[SurveyResponsePersist, WriteResult] _ expects toDataPersist(data, internalAuthId) returning Future.failed(new Exception)

      persist(data, internalAuthId).isLeft shouldBe true
    }
  }

  "findByCampaignAndAuthid" should {

    // for testing of the happy path see SurveyWidgetDataISpec

    "return an error when the future returned by mongo fails" in {
      mockDBFunctions.find _ expects * returning Future.failed(new Exception)

      await(repo.findByCampaignAndAuthid("TEST_CAMPAIGN_ID", "test-auth-id")).isLeft shouldBe true
    }
  }

}
