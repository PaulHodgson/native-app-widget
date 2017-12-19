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

package uk.gov.hmrc.nativeappwidget.services

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import uk.gov.hmrc.nativeappwidget.models._
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SurveyWidgetDataServiceSpec extends WordSpec with Matchers with MockFactory {

  val mockRepo: SurveyWidgetRepository = mock[SurveyWidgetRepository]

  private val validCampaignId = "whitelisted"
  private val validCampaignId2 = "whitelisted2"
  private val notWhitelistedCampaignId = "not-whitelisted"

  val surveyWhitelist = Set(validCampaignId, validCampaignId2)

  val config = ConfigFactory.parseString(
    s"""
       |widget.surveys = [${surveyWhitelist.map(s ⇒ '"' + s + '"').mkString(",")}]
    """.stripMargin)


  val service = new SurveyWidgetDataService(mockRepo, Configuration(config))

  def mockRepoInsert(data: SurveyData, internalAuthId: String)(result: Either[String, DataPersisted]) =
    (mockRepo.persistData(_: SurveyData, _: String))
      .expects(data, internalAuthId)
      .returning(Future.successful(result))

  def await[T](f: Future[T]): T = Await.result(f, 5.seconds)


  private def data(campaignId: String): SurveyData =
    randomData().copy(campaignId = campaignId)

  "addWidgetData" should {

    "return Unauthorised if the campaign ID in the surveyData is not in " +
    "the configured whitelist" in {
      await(service.addWidgetData(data(notWhitelistedCampaignId), "some-internal-auth-id")) shouldBe Left(Unauthorised)
    }

    "return a RepoError if the repo returns an error" in {
      val d = data(validCampaignId)
      val ai = "some-internal-auth-id"
      val message = "uh oh"
      mockRepoInsert(d, ai)(Left(message))

      await(service.addWidgetData(d, ai)) shouldBe Left(RepoError(message))
    }

    "return DataPersisted if the repo returns successfully" in {
      val d = data(validCampaignId)
      val ai = "some-internal-auth-id"
      mockRepoInsert(d, ai)(Right(DataPersisted()))

      await(service.addWidgetData(d, ai)) shouldBe Right(DataPersisted())

    }

  }

}
