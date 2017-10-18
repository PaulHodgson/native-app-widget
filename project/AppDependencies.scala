import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0",
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.9.0",
    "uk.gov.hmrc" %% "domain" % "5.0.0",
    "uk.gov.hmrc" %% "auth-client" % "2.3.0",
    "uk.gov.hmrc" %% "play-ui" % "7.8.0", // for SimpleObjectBinder
    "org.typelevel" %% "cats" % "0.9.0"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.9.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % scope,
    "org.scalacheck" %% "scalacheck" % "1.13.4" % scope
  )

}
