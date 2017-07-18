package uk.gov.hmrc.nativeappwidget.services

import com.google.inject.{ImplementedBy, Inject}
import play.api.Logger
import play.api.libs.json.{JsError, JsValue}
import play.api.mvc._
import uk.gov.hmrc.nativeappwidget.models.{Data, DataPersist}
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository
import uk.gov.hmrc.play.http.BadRequestException

import scala.concurrent.Future

@ImplementedBy(classOf[WidgetDataService])
trait WidgetDataServiceAPI {

  def addWidgetData(json: JsValue): Future[Either[String, DataPersist]]
}

@Singleton
class WidgetDataService @Inject()(repo: SurveyWidgetRepository) extends WidgetDataServiceAPI {
  val logger = Logger(this.getClass)

  def addWidgetData(json: JsValue): Future[Either[String, DataPersist]] = {
    json.validate[Data].fold(
      { e ⇒
        logger.error(s"Could not parse JSON from request: ${prettyPrint(JsError(e))}")
        throw new BadRequestException("Invalid Json Request")
      },
      repo.insertData(_).map(handleRepoInsertResult)
    )

    ???
  }

  private def handleRepoInsertResult(result: Either[String,Unit]): Result =
    result.fold(
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
